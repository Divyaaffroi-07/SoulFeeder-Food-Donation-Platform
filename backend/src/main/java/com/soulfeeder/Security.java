package com.soulfeeder;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.*;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.stereotype.Service;
import org.springframework.web.filter.OncePerRequestFilter;
import javax.crypto.SecretKey;
import java.io.IOException;
import java.util.Date;
import java.util.List;

@Service
class JwtService {
  private final SecretKey key; private final long ttlMs;
  JwtService(@Value("${app.jwt.secret}") String secret, @Value("${app.jwt.hours}") long hours){
    key = Keys.hmacShaKeyFor(secret.getBytes()); ttlMs = hours * 3600_000L; }
  String issue(User u){ Date now = new Date();
    return Jwts.builder().subject(u.email).claim("role", u.role.name()).issuedAt(now).expiration(new Date(now.getTime()+ttlMs)).signWith(key).compact(); }
  String subject(String token){ return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload().getSubject(); }
}

/** Reads "Authorization: Bearer <token>", then loads the user FROM THE DATABASE so the role is never taken from the client. */
class JwtFilter extends OncePerRequestFilter {
  private final JwtService jwt; private final UserRepository users;
  JwtFilter(JwtService j, UserRepository u){ jwt=j; users=u; }
  @Override protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
    String h = req.getHeader("Authorization");
    if (h != null && h.startsWith("Bearer ")) {
      try {
        users.findByEmail(jwt.subject(h.substring(7))).filter(u -> u.enabled).ifPresent(u ->
          SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(u, null, List.of(new SimpleGrantedAuthority("ROLE_" + u.role)))));
      } catch (JwtException | IllegalArgumentException e) { /* bad/expired token => stays unauthenticated => 401 */ }
    }
    chain.doFilter(req, res);
  }
}

@Configuration @EnableWebSecurity
class SecurityConfig {
  @Bean PasswordEncoder passwordEncoder(){ return new BCryptPasswordEncoder(); }
  @Bean SecurityFilterChain chain(HttpSecurity http, JwtService jwt, UserRepository users) throws Exception {
    http.csrf(c -> c.disable())   // safe here: stateless API, token sent in a header (no cookies)
      .cors(Customizer.withDefaults())
      .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
      .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
      .authorizeHttpRequests(a -> a
        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
        .requestMatchers("/api/auth/**", "/api/stats", "/api/images/**", "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
        .requestMatchers(HttpMethod.POST, "/api/donations").hasRole("DONOR")
        .requestMatchers(HttpMethod.POST, "/api/donations/*/claim").hasRole("NGO")
        .requestMatchers(HttpMethod.POST, "/api/donations/*/image").hasRole("DONOR")
        .requestMatchers("/api/admin/**").hasRole("ADMIN")
        .requestMatchers("/api/ngo/**").hasRole("NGO")
        .requestMatchers("/api/volunteer/**").hasRole("VOLUNTEER")
        .requestMatchers(HttpMethod.PATCH, "/api/deliveries/*/status").hasRole("VOLUNTEER")
        .anyRequest().authenticated())
      .addFilterBefore(new JwtFilter(jwt, users), UsernamePasswordAuthenticationFilter.class);
    return http.build(); }
}
