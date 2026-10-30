package com.soulfeeder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.assertThat;

/** The headline interview test: two verified NGOs claim the same donation at the same instant - exactly one may win. */
@SpringBootTest @ActiveProfiles("test")
class ClaimConcurrencyTest {
  @Autowired DonationService svc; @Autowired UserRepository users; @Autowired NgoRepository ngos; @Autowired DonationRepository donations;

  User verifiedNgo(String tag){
    User u = new User(); u.name = "NGO " + tag; u.email = tag + "-" + UUID.randomUUID() + "@t.com"; u.passwordHash = "x"; u.role = Role.NGO; u = users.save(u);
    NgoProfile p = new NgoProfile(); p.userId = u.id; p.orgName = u.name; p.registrationNumber = "R-" + tag; p.address = "Addr " + tag; p.phone = "123"; p.status = VerifStatus.APPROVED; ngos.save(p); return u; }

  @Test void onlyOneNgoWinsWhenTwoClaimAtOnce() throws Exception {
    User a = verifiedNgo("a"), b = verifiedNgo("b");
    Donation d = new Donation(); d.donorName = "Donor"; d.title = "Race food"; d.foodType = "Cooked meal"; d.quantity = 10; d.pickupLocation = "Salem";
    d.preparedAt = LocalDateTime.now().minusHours(1); d.expiresAt = LocalDateTime.now().plusHours(5); d = donations.save(d);
    final Long id = d.id;
    ExecutorService pool = Executors.newFixedThreadPool(2); CountDownLatch go = new CountDownLatch(1); List<Future<Boolean>> results = new ArrayList<>();
    for (User u : List.of(a, b)) results.add(pool.submit(() -> { go.await(); try { svc.claim(id, u); return true; } catch (IllegalStateException e) { return false; } }));
    go.countDown(); int wins = 0; for (Future<Boolean> f : results) if (f.get(30, TimeUnit.SECONDS)) wins++; pool.shutdown();
    assertThat(wins).isEqualTo(1);
    assertThat(donations.findById(id).orElseThrow().status).isEqualTo(Status.CLAIMED); }
}
