package com.soulfeeder;
import org.junit.jupiter.api.*;
import org.springframework.security.access.AccessDeniedException;
import java.time.LocalDateTime;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Business-rule tests with Mockito: no Spring, no database, runs in milliseconds. */
class DonationServiceTest {
  DonationRepository repo = mock(DonationRepository.class); HistoryRepository hist = mock(HistoryRepository.class); NgoRepository ngos = mock(NgoRepository.class);
  DeliveryService dv = mock(DeliveryService.class); NotificationService nt = mock(NotificationService.class); AuditService audit = mock(AuditService.class);
  DonationService svc;
  final LocalDateTime now = LocalDateTime.now();

  @BeforeEach void setUp(){ svc = new DonationService(repo, hist, ngos, dv, nt, audit); when(repo.save(any(Donation.class))).thenAnswer(i -> i.getArgument(0)); }

  static User user(long id, Role role, String name){ User u = new User(); u.id = id; u.role = role; u.name = name; u.email = name + "@x.com"; return u; }
  Donation donation(Status s){ Donation d = new Donation(); d.id = 1L; d.title = "Rice"; d.status = s; d.donorId = 10L; d.expiresAt = now.plusHours(5); return d; }
  void approved(long ngoUserId){ NgoProfile p = new NgoProfile(); p.status = VerifStatus.APPROVED; when(ngos.findByUserId(ngoUserId)).thenReturn(Optional.of(p)); }

  // ---- create ----
  @Test void create_rejectsExpiryNotAfterPreparation(){
    var r = new DonationRequest("Rice", "Cooked meal", 5, "Salem", now, now.minusHours(1), null);
    assertThatThrownBy(() -> svc.create(r, user(10, Role.DONOR, "d"))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("after preparation"); }
  @Test void create_rejectsAlreadyExpiredFood(){
    var r = new DonationRequest("Rice", "Cooked meal", 5, "Salem", now.minusHours(9), now.minusHours(1), null);
    assertThatThrownBy(() -> svc.create(r, user(10, Role.DONOR, "d"))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("expired"); }
  @Test void create_takesDonorIdentityFromTokenUser_notFromRequest(){
    var r = new DonationRequest("Rice", "Cooked meal", 5, "Salem", now.minusHours(1), now.plusHours(4), null);
    Donation d = svc.create(r, user(10, Role.DONOR, "Asha"));
    assertThat(d.donorId).isEqualTo(10L); assertThat(d.donorName).isEqualTo("Asha"); assertThat(d.status).isEqualTo(Status.AVAILABLE); }

  // ---- claim ----
  @Test void claim_unverifiedNgoIsForbidden(){
    when(ngos.findByUserId(20L)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> svc.claim(1L, user(20, Role.NGO, "ngo"))).isInstanceOf(AccessDeniedException.class);
    verify(repo, never()).findForUpdate(any()); }
  @Test void claim_verifiedNgoSucceeds_opensDeliveryTask_notifiesDonor(){
    approved(20L); Donation d = donation(Status.AVAILABLE); when(repo.findForUpdate(1L)).thenReturn(Optional.of(d)); User ngo = user(20, Role.NGO, "Hope");
    Donation r = svc.claim(1L, ngo);
    assertThat(r.status).isEqualTo(Status.CLAIMED); assertThat(r.claimedById).isEqualTo(20L);
    verify(dv).createFor(d, ngo); verify(nt).send(eq(10L), contains("claimed")); }
  @Test void claim_alreadyClaimedIsConflict(){
    approved(20L); when(repo.findForUpdate(1L)).thenReturn(Optional.of(donation(Status.CLAIMED)));
    assertThatThrownBy(() -> svc.claim(1L, user(20, Role.NGO, "n"))).isInstanceOf(IllegalStateException.class).hasMessageContaining("Already claimed"); }
  @Test void claim_expiredDonationCannotBeClaimed(){
    approved(20L); Donation d = donation(Status.AVAILABLE); d.expiresAt = now.minusMinutes(1); when(repo.findForUpdate(1L)).thenReturn(Optional.of(d));
    assertThatThrownBy(() -> svc.claim(1L, user(20, Role.NGO, "n"))).isInstanceOf(IllegalStateException.class).hasMessageContaining("expired"); }

  // ---- status transitions & ownership ----
  @Test void move_ownerCanCancel_andDeliveryTaskIsCancelled(){
    Donation d = donation(Status.AVAILABLE); when(repo.findForUpdate(1L)).thenReturn(Optional.of(d));
    svc.move(1L, Status.CANCELLED, user(10, Role.DONOR, "owner"));
    assertThat(d.status).isEqualTo(Status.CANCELLED); verify(dv).cancelFor(eq(d), anyString()); }
  @Test void move_otherDonorCannotCancel(){
    when(repo.findForUpdate(1L)).thenReturn(Optional.of(donation(Status.AVAILABLE)));
    assertThatThrownBy(() -> svc.move(1L, Status.CANCELLED, user(11, Role.DONOR, "other"))).isInstanceOf(AccessDeniedException.class); }
  @Test void move_donorCannotMarkDelivered(){
    when(repo.findForUpdate(1L)).thenReturn(Optional.of(donation(Status.PICKED_UP)));
    assertThatThrownBy(() -> svc.move(1L, Status.DELIVERED, user(10, Role.DONOR, "owner"))).isInstanceOf(AccessDeniedException.class); }
  @Test void move_invalidJumpIsRejected(){
    when(repo.findForUpdate(1L)).thenReturn(Optional.of(donation(Status.AVAILABLE)));
    assertThatThrownBy(() -> svc.move(1L, Status.DELIVERED, user(1, Role.ADMIN, "admin"))).isInstanceOf(IllegalStateException.class).hasMessageContaining("Cannot move"); }
  @Test void move_ngoBlockedWhileVolunteerIsOnTheJob(){
    Donation d = donation(Status.CLAIMED); d.claimedById = 20L; when(repo.findForUpdate(1L)).thenReturn(Optional.of(d));
    doThrow(new IllegalStateException("A volunteer is handling this delivery")).when(dv).ensureNoVolunteer(d);
    assertThatThrownBy(() -> svc.move(1L, Status.PICKED_UP, user(20, Role.NGO, "ngo"))).isInstanceOf(IllegalStateException.class).hasMessageContaining("volunteer"); }
  @Test void move_adminOverrideIsAudited(){
    Donation d = donation(Status.AVAILABLE); when(repo.findForUpdate(1L)).thenReturn(Optional.of(d)); User admin = user(1, Role.ADMIN, "admin");
    svc.move(1L, Status.CANCELLED, admin); verify(audit).log(eq(admin), eq("DONATION_CANCELLED"), anyString()); }
}
