package com.soulfeeder;
import org.junit.jupiter.api.*;
import org.springframework.security.access.AccessDeniedException;
import java.time.LocalDateTime;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DeliveryServiceTest {
  DeliveryRepository tasks = mock(DeliveryRepository.class); DeliveryHistoryRepository dh = mock(DeliveryHistoryRepository.class); DonationRepository donations = mock(DonationRepository.class);
  HistoryRepository donationHistory = mock(HistoryRepository.class); VolunteerRepository vols = mock(VolunteerRepository.class); NgoRepository ngos = mock(NgoRepository.class); NotificationService notes = mock(NotificationService.class);
  DeliveryService svc; final LocalDateTime now = LocalDateTime.now(); final User vol = DonationServiceTest.user(30, Role.VOLUNTEER, "Ravi");

  @BeforeEach void setUp(){ svc = new DeliveryService(tasks, dh, donations, donationHistory, vols, ngos, notes); }
  DeliveryTask task(DeliveryStatus s){ DeliveryTask t = new DeliveryTask(); t.id = 1L; t.donationId = 2L; t.status = s; t.expiresAt = now.plusHours(5); t.donorId = 10L; t.ngoId = 20L; t.title = "Rice"; return t; }
  Donation donation(Status s, LocalDateTime exp){ Donation d = new Donation(); d.id = 2L; d.status = s; d.expiresAt = exp; return d; }
  void profile(){ when(vols.findByUserId(30L)).thenReturn(Optional.of(new VolunteerProfile())); }
  void lock(DeliveryTask t, Donation d){ when(tasks.findById(1L)).thenReturn(Optional.of(t)); when(donations.findForUpdate(2L)).thenReturn(Optional.of(d)); when(tasks.findForUpdate(1L)).thenReturn(Optional.of(t)); }

  @Test void accept_requiresVolunteerProfile(){ when(vols.findByUserId(30L)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> svc.accept(1L, vol)).isInstanceOf(IllegalStateException.class).hasMessageContaining("profile"); }
  @Test void accept_succeedsAndNotifiesNgoAndDonor(){ profile(); DeliveryTask t = task(DeliveryStatus.OPEN); when(tasks.findForUpdate(1L)).thenReturn(Optional.of(t));
    svc.accept(1L, vol); assertThat(t.status).isEqualTo(DeliveryStatus.ACCEPTED); assertThat(t.volunteerId).isEqualTo(30L);
    verify(notes).send(eq(20L), anyString()); verify(notes).send(eq(10L), anyString()); }
  @Test void accept_secondVolunteerIsRejected(){ profile(); when(tasks.findForUpdate(1L)).thenReturn(Optional.of(task(DeliveryStatus.ACCEPTED)));
    assertThatThrownBy(() -> svc.accept(1L, vol)).isInstanceOf(IllegalStateException.class).hasMessageContaining("no longer open"); }
  @Test void accept_expiredFoodIsRejected(){ profile(); DeliveryTask t = task(DeliveryStatus.OPEN); t.expiresAt = now.minusMinutes(1); when(tasks.findForUpdate(1L)).thenReturn(Optional.of(t));
    assertThatThrownBy(() -> svc.accept(1L, vol)).isInstanceOf(IllegalStateException.class).hasMessageContaining("expired"); }
  @Test void accept_limitsActiveDeliveries(){ profile(); when(tasks.findForUpdate(1L)).thenReturn(Optional.of(task(DeliveryStatus.OPEN)));
    when(tasks.countByVolunteerIdAndStatusIn(eq(30L), anyCollection())).thenReturn(3L);
    assertThatThrownBy(() -> svc.accept(1L, vol)).isInstanceOf(IllegalStateException.class).hasMessageContaining("active deliveries"); }

  @Test void advance_pickupUpdatesTaskAndDonation(){ DeliveryTask t = task(DeliveryStatus.ACCEPTED); t.volunteerId = 30L; Donation d = donation(Status.CLAIMED, now.plusHours(3)); lock(t, d);
    svc.advance(1L, DeliveryStatus.PICKED_UP, vol); assertThat(t.status).isEqualTo(DeliveryStatus.PICKED_UP); assertThat(d.status).isEqualTo(Status.PICKED_UP); verify(donationHistory).save(any(StatusHistory.class)); }
  @Test void advance_foodSafety_expiredFoodCannotBeCollected(){ DeliveryTask t = task(DeliveryStatus.ACCEPTED); t.volunteerId = 30L; lock(t, donation(Status.CLAIMED, now.minusMinutes(5)));
    assertThatThrownBy(() -> svc.advance(1L, DeliveryStatus.PICKED_UP, vol)).isInstanceOf(IllegalStateException.class).hasMessageContaining("expired"); }
  @Test void advance_onlyAssignedVolunteer(){ DeliveryTask t = task(DeliveryStatus.ACCEPTED); t.volunteerId = 99L; lock(t, donation(Status.CLAIMED, now.plusHours(3)));
    assertThatThrownBy(() -> svc.advance(1L, DeliveryStatus.PICKED_UP, vol)).isInstanceOf(AccessDeniedException.class); }
  @Test void advance_cannotSkipPickup(){ DeliveryTask t = task(DeliveryStatus.ACCEPTED); t.volunteerId = 30L; lock(t, donation(Status.CLAIMED, now.plusHours(3)));
    assertThatThrownBy(() -> svc.advance(1L, DeliveryStatus.DELIVERED, vol)).isInstanceOf(IllegalStateException.class).hasMessageContaining("Cannot move"); }
}
