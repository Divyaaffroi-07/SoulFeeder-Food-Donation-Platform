package com.soulfeeder;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import java.util.*;

@RestController @RequestMapping("/api")
class DonationController {
  private final DonationService s; DonationController(DonationService s){ this.s=s; }
  @PostMapping("/donations") @ResponseStatus(HttpStatus.CREATED) Donation create(@Valid @RequestBody DonationRequest r, @AuthenticationPrincipal User u){ return s.create(r, u); }
  @GetMapping("/donations") List<Donation> list(@RequestParam(required=false) Status status, @RequestParam(defaultValue="false") boolean mine, @AuthenticationPrincipal User u){ return s.list(status, mine, u); }
  @GetMapping("/donations/search") PageOf<Donation> search(@RequestParam(required=false) Status status, @RequestParam(required=false) String foodType, @RequestParam(required=false) String q,
    @RequestParam(defaultValue="false") boolean mine, @RequestParam(defaultValue="newest") String sort, @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="9") int size, @AuthenticationPrincipal User u){
    return s.search(status, foodType, q, mine, sort, page, size, u); }
  @GetMapping("/donations/{id}") Donation get(@PathVariable Long id){ return s.get(id); }
  @PostMapping("/donations/{id}/claim") Donation claim(@PathVariable Long id, @AuthenticationPrincipal User u){ return s.claim(id, u); }
  @PatchMapping("/donations/{id}/status") Donation status(@PathVariable Long id, @Valid @RequestBody StatusRequest r, @AuthenticationPrincipal User u){ return s.move(id, r.status(), u); }
  @GetMapping("/donations/{id}/history") List<StatusHistory> history(@PathVariable Long id){ return s.history(id); }
  @GetMapping("/stats") Map<String,Object> stats(){ return s.stats(); }
}

@RestControllerAdvice
class Errors {
  private ResponseEntity<Map<String,Object>> r(HttpStatus h, String m){ return ResponseEntity.status(h).body(Map.of("status",h.value(),"message",m)); }
  @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class) ResponseEntity<?> big(Exception e){ return r(HttpStatus.PAYLOAD_TOO_LARGE,"Image must be 2 MB or smaller"); }
  @ExceptionHandler(NoSuchElementException.class) ResponseEntity<?> nf(Exception e){ return r(HttpStatus.NOT_FOUND,e.getMessage()); }
  @ExceptionHandler(IllegalArgumentException.class) ResponseEntity<?> bad(Exception e){ return r(HttpStatus.BAD_REQUEST,e.getMessage()); }
  @ExceptionHandler(BadCredentialsException.class) ResponseEntity<?> cred(Exception e){ return r(HttpStatus.UNAUTHORIZED,e.getMessage()); }
  @ExceptionHandler(AccessDeniedException.class) ResponseEntity<?> denied(Exception e){ return r(HttpStatus.FORBIDDEN,e.getMessage()); }
  @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class) ResponseEntity<?> dup(Exception e){ return r(HttpStatus.CONFLICT,"That record already exists"); }
  @ExceptionHandler({IllegalStateException.class, org.springframework.orm.ObjectOptimisticLockingFailureException.class}) ResponseEntity<?> conflict(Exception e){ return r(HttpStatus.CONFLICT,e.getMessage()); }
  @ExceptionHandler(MethodArgumentNotValidException.class) ResponseEntity<?> val(MethodArgumentNotValidException e){
    Map<String,String> f=new LinkedHashMap<>(); e.getFieldErrors().forEach(x->f.put(x.getField(),x.getDefaultMessage()));
    return ResponseEntity.badRequest().body(Map.of("status",400,"message","Please fix the highlighted fields","fields",f)); }
}
