package com.soulfeeder;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
import java.time.LocalDateTime;
interface DonationRepository extends JpaRepository<Donation, Long>, JpaSpecificationExecutor<Donation> {
  List<Donation> findAllByOrderByCreatedAtDesc();
  List<Donation> findByStatusOrderByCreatedAtDesc(Status s);
  long countByStatus(Status s);
  @Query("select coalesce(sum(d.quantity),0) from Donation d where d.status = :s") long mealsFor(@Param("s") Status s);
  @Query("select coalesce(sum(d.quantity),0) from Donation d") long totalServings();
  @Query("select d.donorName, sum(d.quantity) from Donation d where d.status = :s group by d.donorName order by sum(d.quantity) desc") List<Object[]> topDonors(@Param("s") Status s, Pageable p);
  @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select d from Donation d where d.id = :id")
  Optional<Donation> findForUpdate(@Param("id") Long id);
}
interface UserRepository extends JpaRepository<User, Long> {
  Optional<User> findByEmail(String e); boolean existsByEmail(String e); long countByRole(Role r);
  @Query("select u from User u where lower(u.name) like lower(concat('%', :q, '%')) or lower(u.email) like lower(concat('%', :q, '%'))")
  Page<User> search(@Param("q") String q, Pageable p);
  @Query("select u from User u where u.role = :role and (lower(u.name) like lower(concat('%', :q, '%')) or lower(u.email) like lower(concat('%', :q, '%')))")
  Page<User> searchByRole(@Param("role") Role role, @Param("q") String q, Pageable p);
}
interface HistoryRepository extends JpaRepository<StatusHistory, Long> { List<StatusHistory> findByDonationIdOrderByAtAsc(Long id); }

interface NgoRepository extends JpaRepository<NgoProfile, Long> {
  Optional<NgoProfile> findByUserId(Long userId);
  long countByStatus(VerifStatus s);
  List<NgoProfile> findAllByOrderByCreatedAtDesc();
  List<NgoProfile> findByStatusOrderByCreatedAtDesc(VerifStatus s);
}
interface AuditRepository extends JpaRepository<AuditLog, Long> { List<AuditLog> findTop50ByOrderByAtDesc(); }

interface DeliveryRepository extends JpaRepository<DeliveryTask, Long> {
  Optional<DeliveryTask> findByDonationId(Long donationId);
  long countByStatus(DeliveryStatus s);
  List<DeliveryTask> findByStatusAndExpiresAtAfterOrderByCreatedAtAsc(DeliveryStatus s, LocalDateTime now);
  List<DeliveryTask> findByVolunteerIdOrderByCreatedAtDesc(Long volunteerId);
  long countByVolunteerIdAndStatusIn(Long volunteerId, Collection<DeliveryStatus> s);
  @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select t from DeliveryTask t where t.id = :id")
  Optional<DeliveryTask> findForUpdate(@Param("id") Long id);
}
interface DeliveryHistoryRepository extends JpaRepository<DeliveryHistory, Long> { List<DeliveryHistory> findByTaskIdOrderByAtAsc(Long taskId); }
interface VolunteerRepository extends JpaRepository<VolunteerProfile, Long> { Optional<VolunteerProfile> findByUserId(Long userId); }
interface NotificationRepository extends JpaRepository<Notification, Long> {
  List<Notification> findTop30ByUserIdOrderByAtDesc(Long userId);
  @Modifying @Query("update Notification n set n.seen = true where n.userId = :u") int markAll(@Param("u") Long u);
}
