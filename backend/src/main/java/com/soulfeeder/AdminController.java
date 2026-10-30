package com.soulfeeder;
import jakarta.validation.Valid;
import org.springframework.data.domain.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.*;

@Service
class AuditService {
  private final AuditRepository repo; AuditService(AuditRepository r){ repo=r; }
  void log(User actor, String action, String details){ AuditLog a=new AuditLog(); a.actorId=actor.id; a.actorName=actor.name; a.action=action; a.details=details; repo.save(a); }
}

@RestController @RequestMapping("/api/admin")
class AdminController {
  record UserView(Long id, String name, String email, Role role, boolean enabled, LocalDateTime createdAt) {}
  private final UserRepository users; private final DonationRepository donations; private final NgoRepository ngos; private final DeliveryRepository deliveries; private final AuditService audit;
  AdminController(UserRepository u, DonationRepository d, NgoRepository n, DeliveryRepository dl, AuditService a){ users=u; donations=d; ngos=n; deliveries=dl; audit=a; }
  private static UserView view(User u){ return new UserView(u.id, u.name, u.email, u.role, u.enabled, u.createdAt); }

  /** Every number here comes from a database query. */
  @GetMapping("/dashboard")
  Map<String,Object> dashboard(){
    Map<String,Object> m = new LinkedHashMap<>();
    Map<String,Long> byRole = new LinkedHashMap<>(); for (Role r : Role.values()) byRole.put(r.name(), users.countByRole(r));
    Map<String,Long> byStatus = new LinkedHashMap<>(); for (Status s : Status.values()) byStatus.put(s.name(), donations.countByStatus(s));
    m.put("usersByRole", byRole); m.put("donationsByStatus", byStatus);
    m.put("mealsDelivered", donations.mealsFor(Status.DELIVERED)); m.put("servingsPosted", donations.totalServings());
    m.put("ngosPending", ngos.countByStatus(VerifStatus.PENDING)); m.put("ngosApproved", ngos.countByStatus(VerifStatus.APPROVED));
    m.put("openTasks", deliveries.countByStatus(DeliveryStatus.OPEN)); m.put("completedDeliveries", deliveries.countByStatus(DeliveryStatus.DELIVERED));
    m.put("topDonors", donations.topDonors(Status.DELIVERED, PageRequest.of(0, 5)).stream().map(o -> Map.of("name", o[0], "servings", o[1])).toList());
    return m; }

  @GetMapping("/users")
  PageOf<UserView> users(@RequestParam(defaultValue="") String q, @RequestParam(required=false) Role role, @RequestParam(defaultValue="0") int page,
      @RequestParam(defaultValue="10") int size, @RequestParam(defaultValue="newest") String sort){
    Sort so = switch (sort) { case "name" -> Sort.by("name").ascending(); case "role" -> Sort.by("role").ascending(); default -> Sort.by("createdAt").descending(); };
    Pageable p = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50), so);
    Page<User> r = role == null ? users.search(q.trim(), p) : users.searchByRole(role, q.trim(), p);
    return new PageOf<>(r.getContent().stream().map(AdminController::view).toList(), r.getNumber(), r.getSize(), r.getTotalElements(), r.getTotalPages()); }

  /** Suspended users are rejected by the JWT filter on their very next request. Admins cannot be suspended. */
  @PatchMapping("/users/{id}/suspension") @Transactional
  UserView suspend(@PathVariable Long id, @Valid @RequestBody SuspendRequest r, @AuthenticationPrincipal User admin){
    User t = users.findById(id).orElseThrow(() -> new NoSuchElementException("User not found"));
    if (t.role == Role.ADMIN) throw new IllegalStateException("Admin accounts cannot be suspended");
    t.enabled = !r.suspended(); users.save(t);
    audit.log(admin, r.suspended() ? "USER_SUSPENDED" : "USER_REINSTATED", t.email + (r.reason() == null || r.reason().isBlank() ? "" : " - " + r.reason()));
    return view(t); }
}
