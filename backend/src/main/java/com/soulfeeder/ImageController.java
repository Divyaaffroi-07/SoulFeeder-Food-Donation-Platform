package com.soulfeeder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;

@RestController @RequestMapping("/api")
class ImageController {
  private final DonationRepository repo; private final Path dir;
  ImageController(DonationRepository r, @Value("${app.upload.dir}") String d){ repo=r; dir=Paths.get(d).toAbsolutePath().normalize(); }

  /** Never trust the filename or Content-Type from the client: sniff the real file signature and generate our own name. */
  private static String detect(byte[] b){
    if (b.length > 12 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) return "jpg";
    if (b.length > 12 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') return "png";
    if (b.length > 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F' && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') return "webp";
    return null; }

  @PostMapping("/donations/{id}/image") @Transactional
  Donation upload(@PathVariable Long id, @RequestParam("file") MultipartFile f, @AuthenticationPrincipal User u) throws IOException {
    Donation d = repo.findById(id).orElseThrow(() -> new NoSuchElementException("Donation not found"));
    if (!u.id.equals(d.donorId)) throw new AccessDeniedException("Only the donor can add a photo");
    if (f.isEmpty()) throw new IllegalArgumentException("Choose an image first");
    byte[] bytes = f.getBytes(); String ext = detect(bytes);
    if (ext == null) throw new IllegalArgumentException("Only JPEG, PNG or WebP images are allowed");
    Files.createDirectories(dir); String name = UUID.randomUUID() + "." + ext; Files.write(dir.resolve(name), bytes);
    if (d.imageName != null) Files.deleteIfExists(dir.resolve(d.imageName));
    d.imageName = name; return d; }

  @GetMapping("/images/{name}")
  ResponseEntity<byte[]> image(@PathVariable String name) throws IOException {
    if (!name.matches("[a-f0-9-]{36}\\.(jpg|png|webp)")) throw new NoSuchElementException("Image not found");
    Path p = dir.resolve(name).normalize();
    if (!p.startsWith(dir) || !Files.exists(p)) throw new NoSuchElementException("Image not found");
    MediaType mt = name.endsWith(".png") ? MediaType.IMAGE_PNG : name.endsWith(".webp") ? MediaType.parseMediaType("image/webp") : MediaType.IMAGE_JPEG;
    return ResponseEntity.ok().contentType(mt).header("X-Content-Type-Options", "nosniff").cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePublic()).body(Files.readAllBytes(p)); }
}
