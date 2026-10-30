package com.soulfeeder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import java.util.*;

@Service
class DonationService {
  private final DonationRepository repo; private final HistoryRepository hist;
  private final NgoRepository ngos; private final DeliveryService dv; private final NotificationService nt; private final AuditService audit;
  DonationService(DonationRepository r, HistoryRepository h, NgoRepository n, DeliveryService dv, NotificationService nt, AuditService audit){ repo=r; hist=h; ngos=n; this.dv=dv; this.nt=nt; this.audit=audit; }
  private static final Map<Status,Set<Status>> NEXT = Map.of(
    Status.AVAILABLE, Set.of(Status.CLAIMED, Status.CANCELLED),
    Status.CLAIMED, Set.of(Status.PICKED_UP, Status.CANCELLED),
    Status.PICKED_UP, Set.of(Status.DELIVERED),
    Status.DELIVERED, Set.of(), Status.CANCELLED, Set.of());

  @Transactional Donation create(DonationRequest r, User donor){
    if(!r.expiresAt().isAfter(r.preparedAt())) throw new IllegalArgumentException("Expiry must be after preparation time");
    if(!r.expiresAt().isAfter(LocalDateTime.now())) throw new IllegalArgumentException("This food is already expired");
    Donation d = new Donation();
    d.donorName=donor.name; d.donorId=donor.id;   // identity comes from the token, not the request body
    d.title=r.title(); d.foodType=r.foodType(); d.quantity=r.quantity();
    d.pickupLocation=r.pickupLocation(); d.preparedAt=r.preparedAt(); d.expiresAt=r.expiresAt(); d.safetyNotes=r.safetyNotes();
    d = repo.save(d); log(d,"Donation posted by "+d.donorName); return d;
  }
  List<Donation> list(Status s, boolean mine, User u){
    List<Donation> all = s==null ? repo.findAllByOrderByCreatedAtDesc() : repo.findByStatusOrderByCreatedAtDesc(s);
    if(!mine) return all;
    return all.stream().filter(d -> u.id.equals(d.donorId) || u.id.equals(d.claimedById)).toList();
  }
  Donation get(Long id){ return repo.findById(id).orElseThrow(() -> new NoSuchElementException("Donation not found")); }
  List<StatusHistory> history(Long id){ get(id); return hist.findByDonationIdOrderByAtAsc(id); }

  /** Row lock (SELECT ... FOR UPDATE) => two NGOs can never both claim the same donation. */
  @Transactional Donation claim(Long id, User ngo){
    if (ngos.findByUserId(ngo.id).filter(p -> p.status == VerifStatus.APPROVED).isEmpty())
      throw new AccessDeniedException("Your NGO must be verified by an admin before claiming food");
    Donation d = repo.findForUpdate(id).orElseThrow(() -> new NoSuchElementException("Donation not found"));
    if(d.status!=Status.AVAILABLE) throw new IllegalStateException("Already claimed or no longer available");
    if(!d.expiresAt.isAfter(LocalDateTime.now())) throw new IllegalStateException("Donation has expired");
    d.status=Status.CLAIMED; d.claimedBy=ngo.name; d.claimedById=ngo.id; log(d,"Claimed by "+ngo.name);
    dv.createFor(d, ngo); nt.send(d.donorId, ngo.name+" claimed your donation: "+d.title); return d;
  }
  @Transactional Donation move(Long id, Status to, User actor){
    Donation d = repo.findForUpdate(id).orElseThrow(() -> new NoSuchElementException("Donation not found"));
    boolean ok = switch (actor.role) {
      case ADMIN -> true;
      case DONOR -> actor.id.equals(d.donorId) && to == Status.CANCELLED;                       // owner may cancel
      case NGO -> actor.id.equals(d.claimedById) && (to == Status.PICKED_UP || to == Status.DELIVERED); // claimer drives delivery
      default -> false;
    };
    if(!ok) throw new AccessDeniedException("You are not allowed to make this change");
    if(!NEXT.get(d.status).contains(to)) throw new IllegalStateException("Cannot move from "+d.status+" to "+to);
    if(to==Status.PICKED_UP || to==Status.DELIVERED) dv.ensureNoVolunteer(d);
    if(to==Status.CANCELLED){ dv.cancelFor(d, "Donation cancelled by "+actor.name); nt.send(d.claimedById, "Donation cancelled: "+d.title); }
    if(actor.role==Role.ADMIN) audit.log(actor, "DONATION_" + to, d.title + " (#" + d.id + ")");
    d.status=to; log(d,"Status changed to "+to+" by "+actor.name); return d;
  }
  /** Paged, sortable, filterable listing. The sort key is whitelisted and the page size is capped. */
  PageOf<Donation> search(Status status, String foodType, String q, boolean mine, String sort, int page, int size, User u){
    Specification<Donation> spec = (root, cq, cb) -> {
      List<Predicate> p = new ArrayList<>();
      if(status != null) p.add(cb.equal(root.get("status"), status));
      if(foodType != null && !foodType.isBlank()) p.add(cb.equal(root.get("foodType"), foodType));
      if(q != null && !q.isBlank()){ String like = "%" + q.trim().toLowerCase() + "%";
        p.add(cb.or(cb.like(cb.lower(root.get("title")), like), cb.like(cb.lower(root.get("pickupLocation")), like), cb.like(cb.lower(root.get("donorName")), like))); }
      if(mine) p.add(cb.or(cb.equal(root.get("donorId"), u.id), cb.equal(root.get("claimedById"), u.id)));
      return cb.and(p.toArray(new Predicate[0])); };
    Sort so = switch (sort == null ? "newest" : sort) {
      case "expiry" -> Sort.by("expiresAt").ascending();
      case "quantity" -> Sort.by("quantity").descending();
      default -> Sort.by("createdAt").descending(); };
    Page<Donation> r = repo.findAll(spec, PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50), so));
    return new PageOf<>(r.getContent(), r.getNumber(), r.getSize(), r.getTotalElements(), r.getTotalPages()); }

  Map<String,Object> stats(){
    Map<String,Object> m = new LinkedHashMap<>();
    for(Status s: Status.values()) m.put(s.name(), repo.countByStatus(s));
    m.put("MEALS_DELIVERED", repo.mealsFor(Status.DELIVERED)); m.put("TOTAL", repo.count()); return m;
  }
  void log(Donation d, String note){ StatusHistory h=new StatusHistory(); h.donationId=d.id; h.status=d.status; h.note=note; hist.save(h); }
}
