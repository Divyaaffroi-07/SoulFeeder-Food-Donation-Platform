package com.soulfeeder;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController @RequestMapping("/api")
class NgoController {
  private final NgoRepository ngos; private final UserRepository users; private final AuditRepository audit;
  NgoController(NgoRepository n, UserRepository u, AuditRepository a){ ngos=n; users=u; audit=a; }

  /** NGO: see my own application (204 if I have not applied yet). */
  @GetMapping("/ngo/verification")
  ResponseEntity<NgoProfile> mine(@AuthenticationPrincipal User u){
    return ngos.findByUserId(u.id).map(ResponseEntity::ok).orElse(ResponseEntity.noContent().build()); }

  /** NGO: apply, or re-apply after a rejection. Pending/approved applications are locked so verified details cannot be swapped silently. */
  @PutMapping("/ngo/verification") @Transactional
  NgoProfile submit(@Valid @RequestBody NgoRequest r, @AuthenticationPrincipal User u){
    NgoProfile p = ngos.findByUserId(u.id).orElseGet(NgoProfile::new);
    if (p.id != null && p.status != VerifStatus.REJECTED) throw new IllegalStateException("Your application is already " + p.status + " and cannot be edited");
    p.userId=u.id; p.orgName=r.orgName().trim(); p.registrationNumber=r.registrationNumber().trim(); p.address=r.address().trim();
    p.phone=r.phone().trim(); p.description=r.description();
    p.status=VerifStatus.PENDING; p.reviewNote=null; p.reviewedBy=null; p.reviewedAt=null;
    return ngos.save(p); }

  @GetMapping("/admin/ngos")
  List<NgoView> list(@RequestParam(required=false) VerifStatus status){
    List<NgoProfile> l = status==null ? ngos.findAllByOrderByCreatedAtDesc() : ngos.findByStatusOrderByCreatedAtDesc(status);
    Map<Long,User> um = users.findAllById(l.stream().map(p -> p.userId).toList()).stream().collect(Collectors.toMap(x -> x.id, x -> x));
    return l.stream().map(p -> view(p, um.get(p.userId))).toList(); }

  @PatchMapping("/admin/ngos/{id}/verification") @Transactional
  NgoView review(@PathVariable Long id, @Valid @RequestBody ReviewRequest r, @AuthenticationPrincipal User admin){
    if (r.status() == VerifStatus.PENDING) throw new IllegalArgumentException("Decision must be APPROVED or REJECTED");
    if (r.status() == VerifStatus.REJECTED && (r.note() == null || r.note().isBlank())) throw new IllegalArgumentException("A reason is required when rejecting");
    NgoProfile p = ngos.findById(id).orElseThrow(() -> new NoSuchElementException("NGO application not found"));
    if (p.status != VerifStatus.PENDING) throw new IllegalStateException("This application was already " + p.status);
    p.status=r.status(); p.reviewNote=r.note(); p.reviewedBy=admin.id; p.reviewedAt=LocalDateTime.now(); ngos.save(p);
    AuditLog a = new AuditLog(); a.actorId=admin.id; a.actorName=admin.name; a.action="NGO_" + r.status(); a.details=p.orgName + " (application #" + p.id + ")"; audit.save(a);
    return view(p, users.findById(p.userId).orElse(null)); }

  @GetMapping("/admin/audit") List<AuditLog> audit(){ return audit.findTop50ByOrderByAtDesc(); }

  private NgoView view(NgoProfile p, User x){
    return new NgoView(p.id, p.orgName, p.registrationNumber, p.address, p.phone, p.description, p.status, p.reviewNote, p.createdAt,
      x==null ? "" : x.name, x==null ? "" : x.email); }
}
