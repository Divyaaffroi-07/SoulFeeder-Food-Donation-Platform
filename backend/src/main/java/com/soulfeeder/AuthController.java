package com.soulfeeder;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api")
class AuthController {
  private final UserRepository users; private final PasswordEncoder enc; private final JwtService jwt;
  AuthController(UserRepository u, PasswordEncoder e, JwtService j){ users=u; enc=e; jwt=j; }
  private AuthResponse res(User u, boolean token){ return new AuthResponse(token ? jwt.issue(u) : null, u.id, u.name, u.email, u.role); }

  @PostMapping("/auth/register") @ResponseStatus(HttpStatus.CREATED)
  AuthResponse register(@Valid @RequestBody RegisterRequest r){
    if (r.role() == Role.ADMIN) throw new AccessDeniedException("Admin accounts cannot be self-registered");
    String email = r.email().trim().toLowerCase();
    if (users.existsByEmail(email)) throw new IllegalStateException("This email is already registered");
    User u = new User(); u.name = r.name().trim(); u.email = email; u.role = r.role(); u.passwordHash = enc.encode(r.password());
    return res(users.save(u), true); }

  @PostMapping("/auth/login")
  AuthResponse login(@Valid @RequestBody LoginRequest r){
    User u = users.findByEmail(r.email().trim().toLowerCase()).filter(x -> x.enabled && enc.matches(r.password(), x.passwordHash))
      .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));
    return res(u, true); }

  @PutMapping("/users/me") AuthResponse rename(@Valid @RequestBody ProfileRequest r, @AuthenticationPrincipal User u){ u.name = r.name().trim(); return res(users.save(u), false); }
  @PostMapping("/users/me/password") @ResponseStatus(HttpStatus.NO_CONTENT)
  void password(@Valid @RequestBody PasswordRequest r, @AuthenticationPrincipal User u){
    if (!enc.matches(r.currentPassword(), u.passwordHash)) throw new IllegalArgumentException("Current password is incorrect");
    u.passwordHash = enc.encode(r.newPassword()); users.save(u); }
  @GetMapping("/users/me") AuthResponse me(@AuthenticationPrincipal User u){ return res(u, false); }
}
