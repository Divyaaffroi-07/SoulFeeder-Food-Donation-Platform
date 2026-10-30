package com.soulfeeder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.*;
import org.springframework.web.servlet.config.annotation.*;
import java.time.LocalDateTime;

@Configuration
class Config implements WebMvcConfigurer {
  @Value("${app.cors.origin}") String origin;
  @Override public void addCorsMappings(CorsRegistry r){ r.addMapping("/api/**").allowedOrigins(origin).allowedMethods("GET","POST","PATCH","PUT","DELETE"); }

  /** First admin is created only from environment variables (never hard-coded). */
  @Bean CommandLineRunner adminSeed(UserRepository users, org.springframework.security.crypto.password.PasswordEncoder enc,
      @Value("${app.admin.email:}") String email, @Value("${app.admin.password:}") String pw){ return a -> {
    if (email.isBlank() || pw.length() < 8 || users.existsByEmail(email.toLowerCase())) return;
    User u = new User(); u.name = "Admin"; u.email = email.toLowerCase(); u.role = Role.ADMIN; u.passwordHash = enc.encode(pw); users.save(u); }; }

  /** SAMPLE data for local development only (flagged sample=true). */
  @Bean CommandLineRunner seed(DonationRepository repo, DonationService svc){ return a -> {
    if(repo.count()>0) return;
    LocalDateTime n=LocalDateTime.now();
    String[][] rows={{"Sunrise Bakery","Fresh bread loaves","Bakery","40","MG Road, Salem"},
      {"Green Leaf Restaurant","Veg biryani trays","Cooked meal","60","Fairlands, Salem"},
      {"Priya's Wedding Hall","Event leftovers (sealed)","Event food","120","Hasthampatti, Salem"}};
    for(String[] x: rows){ Donation d=new Donation(); d.donorName=x[0]; d.title=x[1]; d.foodType=x[2]; d.quantity=Integer.parseInt(x[3]); d.pickupLocation=x[4];
      d.preparedAt=n.minusHours(1); d.expiresAt=n.plusHours(8); d.safetyNotes="Keep covered. Consume within 6 hours."; d.sample=true;
      d=repo.save(d); svc.log(d,"Sample donation (local dev data)"); } }; }
}
