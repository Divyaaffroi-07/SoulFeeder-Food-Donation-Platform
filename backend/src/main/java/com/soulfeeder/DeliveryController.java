package com.soulfeeder;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/api")
class DeliveryController {
  private final DeliveryService svc; private final VolunteerRepository vols; private final NotificationRepository notes;
  DeliveryController(DeliveryService s, VolunteerRepository v, NotificationRepository n){ svc=s; vols=v; notes=n; }

  @GetMapping("/volunteer/tasks") List<DeliveryTask> tasks(@RequestParam(defaultValue="open") String scope, @AuthenticationPrincipal User u){ return svc.list(scope, u); }
  @PostMapping("/volunteer/tasks/{id}/accept") DeliveryTask accept(@PathVariable Long id, @AuthenticationPrincipal User u){ return svc.accept(id, u); }
  @PatchMapping("/deliveries/{id}/status") DeliveryTask status(@PathVariable Long id, @Valid @RequestBody DeliveryStatusRequest r, @AuthenticationPrincipal User u){ return svc.advance(id, r.status(), u); }
  @GetMapping("/deliveries/{id}/history") List<DeliveryHistory> history(@PathVariable Long id, @AuthenticationPrincipal User u){ return svc.history(id, u); }
  @GetMapping("/donations/{id}/delivery") ResponseEntity<DeliveryTask> forDonation(@PathVariable Long id, @AuthenticationPrincipal User u){
    return svc.forDonation(id, u).map(ResponseEntity::ok).orElse(ResponseEntity.noContent().build()); }

  @GetMapping("/volunteer/profile") ResponseEntity<VolunteerProfile> profile(@AuthenticationPrincipal User u){
    return vols.findByUserId(u.id).map(ResponseEntity::ok).orElse(ResponseEntity.noContent().build()); }
  @PutMapping("/volunteer/profile") VolunteerProfile save(@Valid @RequestBody VolunteerRequest r, @AuthenticationPrincipal User u){
    VolunteerProfile p = vols.findByUserId(u.id).orElseGet(VolunteerProfile::new); p.userId=u.id; p.phone=r.phone().trim(); p.vehicle=r.vehicle().trim(); p.area=r.area().trim(); return vols.save(p); }

  @GetMapping("/notifications") List<Notification> inbox(@AuthenticationPrincipal User u){ return notes.findTop30ByUserIdOrderByAtDesc(u.id); }
  @PostMapping("/notifications/read-all") @Transactional ResponseEntity<Void> readAll(@AuthenticationPrincipal User u){ notes.markAll(u.id); return ResponseEntity.noContent().build(); }
}
