package com.soulfeeder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;

@Service
class NotificationService {
  private final NotificationRepository repo; NotificationService(NotificationRepository r){ repo=r; }
  void send(Long userId, String msg){ if(userId==null) return; Notification n=new Notification(); n.userId=userId; n.message=msg; repo.save(n); }
}

@Service
class DeliveryService {
  static final int MAX_ACTIVE = 3;
  private final DeliveryRepository tasks; private final DeliveryHistoryRepository dh; private final DonationRepository donations;
  private final HistoryRepository donationHistory; private final VolunteerRepository vols; private final NgoRepository ngos; private final NotificationService notes;
  DeliveryService(DeliveryRepository t, DeliveryHistoryRepository h, DonationRepository d, HistoryRepository dhist, VolunteerRepository v, NgoRepository n, NotificationService ns){
    tasks=t; dh=h; donations=d; donationHistory=dhist; vols=v; ngos=n; notes=ns; }

  private void log(DeliveryTask t, String note){ DeliveryHistory h=new DeliveryHistory(); h.taskId=t.id; h.status=t.status; h.note=note; dh.save(h); }

  /** Called inside the claim transaction: every claimed donation gets exactly one delivery task. */
  void createFor(Donation d, User ngo){
    DeliveryTask t = new DeliveryTask();
    t.donationId=d.id; t.donorId=d.donorId; t.ngoId=ngo.id; t.ngoName=ngo.name; t.title=d.title; t.quantity=d.quantity;
    t.pickupAddress=d.pickupLocation; t.dropoffAddress=ngos.findByUserId(ngo.id).map(p -> p.address).orElse("NGO address on file");
    t.expiresAt=d.expiresAt; t=tasks.save(t); log(t, "Delivery task opened - waiting for a volunteer"); }

  void cancelFor(Donation d, String why){
    tasks.findByDonationId(d.id).filter(t -> t.status==DeliveryStatus.OPEN || t.status==DeliveryStatus.ACCEPTED).ifPresent(t -> {
      t.status=DeliveryStatus.CANCELLED; log(t, why); notes.send(t.volunteerId, "Delivery cancelled: " + t.title); }); }

  /** NGO/admin may not override a volunteer who is already on the job; an unclaimed task is closed if the NGO delivers itself. */
  void ensureNoVolunteer(Donation d){
    tasks.findByDonationId(d.id).ifPresent(t -> {
      if(t.status==DeliveryStatus.ACCEPTED || t.status==DeliveryStatus.PICKED_UP) throw new IllegalStateException("A volunteer is handling this delivery");
      if(t.status==DeliveryStatus.OPEN){ t.status=DeliveryStatus.CANCELLED; log(t, "NGO is delivering directly"); } }); }

  List<DeliveryTask> list(String scope, User v){
    return "mine".equals(scope) ? tasks.findByVolunteerIdOrderByCreatedAtDesc(v.id)
      : tasks.findByStatusAndExpiresAtAfterOrderByCreatedAtAsc(DeliveryStatus.OPEN, LocalDateTime.now()); }

  /** Row lock on the task => two volunteers can never both accept it. */
  @Transactional DeliveryTask accept(Long id, User v){
    if(vols.findByUserId(v.id).isEmpty()) throw new IllegalStateException("Complete your volunteer profile first");
    DeliveryTask t = tasks.findForUpdate(id).orElseThrow(() -> new NoSuchElementException("Task not found"));
    if(t.status!=DeliveryStatus.OPEN) throw new IllegalStateException("This task is no longer open");
    if(!t.expiresAt.isAfter(LocalDateTime.now())) throw new IllegalStateException("This food has expired");
    if(tasks.countByVolunteerIdAndStatusIn(v.id, List.of(DeliveryStatus.ACCEPTED, DeliveryStatus.PICKED_UP)) >= MAX_ACTIVE)
      throw new IllegalStateException("You already have " + MAX_ACTIVE + " active deliveries");
    t.status=DeliveryStatus.ACCEPTED; t.volunteerId=v.id; t.volunteerName=v.name; log(t, "Accepted by " + v.name);
    notes.send(t.ngoId, "Volunteer " + v.name + " will deliver " + t.title); notes.send(t.donorId, "Volunteer " + v.name + " accepted pickup of " + t.title);
    return t; }

  /** Lock order is donation THEN task everywhere, which avoids deadlocks. */
  @Transactional DeliveryTask advance(Long id, DeliveryStatus to, User v){
    DeliveryTask t0 = tasks.findById(id).orElseThrow(() -> new NoSuchElementException("Task not found"));
    Donation d = donations.findForUpdate(t0.donationId).orElseThrow(() -> new NoSuchElementException("Donation not found"));
    DeliveryTask t = tasks.findForUpdate(id).orElseThrow(() -> new NoSuchElementException("Task not found"));
    if(!v.id.equals(t.volunteerId)) throw new AccessDeniedException("This is not your delivery");
    boolean ok = (t.status==DeliveryStatus.ACCEPTED && to==DeliveryStatus.PICKED_UP) || (t.status==DeliveryStatus.PICKED_UP && to==DeliveryStatus.DELIVERED);
    if(!ok) throw new IllegalStateException("Cannot move from " + t.status + " to " + to);
    if(to==DeliveryStatus.PICKED_UP && !d.expiresAt.isAfter(LocalDateTime.now())) throw new IllegalStateException("This food has expired - do not collect it");
    t.status=to; d.status = to==DeliveryStatus.PICKED_UP ? Status.PICKED_UP : Status.DELIVERED;
    String note = (to==DeliveryStatus.PICKED_UP ? "Picked up by " : "Delivered by ") + v.name;
    log(t, note); StatusHistory h=new StatusHistory(); h.donationId=d.id; h.status=d.status; h.note=note; donationHistory.save(h);
    String msg = (to==DeliveryStatus.PICKED_UP ? "On the way: " : "Delivered: ") + t.title; notes.send(t.donorId, msg); notes.send(t.ngoId, msg);
    return t; }

  private void requireAccess(DeliveryTask t, User u){
    if(!(u.role==Role.ADMIN || u.id.equals(t.donorId) || u.id.equals(t.ngoId) || u.id.equals(t.volunteerId))) throw new AccessDeniedException("You cannot view this delivery"); }
  List<DeliveryHistory> history(Long id, User u){
    DeliveryTask t = tasks.findById(id).orElseThrow(() -> new NoSuchElementException("Task not found")); requireAccess(t, u); return dh.findByTaskIdOrderByAtAsc(id); }
  Optional<DeliveryTask> forDonation(Long donationId, User u){
    Optional<DeliveryTask> t = tasks.findByDonationId(donationId); t.ifPresent(x -> requireAccess(x, u)); return t; }
}
