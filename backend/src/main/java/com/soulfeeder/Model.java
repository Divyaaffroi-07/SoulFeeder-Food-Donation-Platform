package com.soulfeeder;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.List;

enum Role { DONOR, NGO, VOLUNTEER, ADMIN }
enum Status { AVAILABLE, CLAIMED, PICKED_UP, DELIVERED, CANCELLED }

@Entity @Table(name="donations", indexes=@Index(columnList="status"))
class Donation {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
  @Column(nullable=false) public String donorName;
  @Column(nullable=false) public String title;
  @Column(nullable=false) public String foodType;
  @Column(nullable=false) public int quantity;
  @Column(nullable=false) public String pickupLocation;
  @Column(nullable=false) public LocalDateTime preparedAt;
  @Column(nullable=false) public LocalDateTime expiresAt;
  @Column(length=500) public String safetyNotes;
  @Enumerated(EnumType.STRING) @Column(nullable=false) public Status status = Status.AVAILABLE;
  public String claimedBy;
  public Long donorId;
  public Long claimedById;
  public String imageName;
  public boolean sample;
  public LocalDateTime createdAt = LocalDateTime.now();
  @Version public Long version;
}

@Entity @Table(name="status_history")
class StatusHistory {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
  @Column(nullable=false) public Long donationId;
  @Enumerated(EnumType.STRING) public Status status;
  public String note;
  public LocalDateTime at = LocalDateTime.now();
}

record DonationRequest(
  @NotBlank String title, @NotBlank String foodType,
  @Min(1) int quantity, @NotBlank String pickupLocation,
  @NotNull LocalDateTime preparedAt, @NotNull LocalDateTime expiresAt, @Size(max=500) String safetyNotes) {}
@Entity @Table(name="users", uniqueConstraints=@UniqueConstraint(columnNames="email"))
class User {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
  @Column(nullable=false) public String name;
  @Column(nullable=false) public String email;
  @Column(nullable=false) public String passwordHash;   // BCrypt hash, never returned by any API
  @Enumerated(EnumType.STRING) @Column(nullable=false) public Role role;
  public boolean enabled = true;
  public LocalDateTime createdAt = LocalDateTime.now();
}
record RegisterRequest(@NotBlank @Size(max=80) String name, @NotBlank @Email String email,
  @NotBlank @Size(min=8, max=72, message="Password must be 8-72 characters") String password, @NotNull Role role) {}
record LoginRequest(@NotBlank String email, @NotBlank String password) {}
record AuthResponse(String token, Long id, String name, String email, Role role) {}
record StatusRequest(@NotNull Status status) {}

enum VerifStatus { PENDING, APPROVED, REJECTED }

@Entity @Table(name="ngo_profiles", uniqueConstraints=@UniqueConstraint(columnNames="userId"), indexes=@Index(columnList="status"))
class NgoProfile {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
  @Column(nullable=false) public Long userId;
  @Column(nullable=false) public String orgName;
  @Column(nullable=false) public String registrationNumber;
  @Column(nullable=false) public String address;
  @Column(nullable=false) public String phone;
  @Column(length=500) public String description;
  @Enumerated(EnumType.STRING) @Column(nullable=false) public VerifStatus status = VerifStatus.PENDING;
  @Column(length=300) public String reviewNote;
  public Long reviewedBy;
  public LocalDateTime reviewedAt;
  public LocalDateTime createdAt = LocalDateTime.now();
}

@Entity @Table(name="audit_logs", indexes=@Index(columnList="at"))
class AuditLog {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
  public Long actorId; public String actorName;
  @Column(nullable=false) public String action;
  @Column(length=500) public String details;
  public LocalDateTime at = LocalDateTime.now();
}

record NgoRequest(@NotBlank @Size(max=120) String orgName, @NotBlank @Size(max=60) String registrationNumber,
  @NotBlank @Size(max=250) String address, @NotBlank @Size(max=20) String phone, @Size(max=500) String description) {}
record ReviewRequest(@NotNull VerifStatus status, @Size(max=300) String note) {}
record NgoView(Long id, String orgName, String registrationNumber, String address, String phone, String description,
  VerifStatus status, String reviewNote, LocalDateTime createdAt, String userName, String userEmail) {}

enum DeliveryStatus { OPEN, ACCEPTED, PICKED_UP, DELIVERED, CANCELLED }

@Entity @Table(name="delivery_tasks", uniqueConstraints=@UniqueConstraint(columnNames="donationId"), indexes={@Index(columnList="status"),@Index(columnList="volunteerId")})
class DeliveryTask {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
  @Column(nullable=false) public Long donationId;
  public Long donorId; public Long ngoId; public String ngoName;
  public String title; public int quantity;
  public String pickupAddress; public String dropoffAddress;
  public LocalDateTime expiresAt;
  @Enumerated(EnumType.STRING) @Column(nullable=false) public DeliveryStatus status = DeliveryStatus.OPEN;
  public Long volunteerId; public String volunteerName;
  public LocalDateTime createdAt = LocalDateTime.now();
}

@Entity @Table(name="delivery_status_history", indexes=@Index(columnList="taskId"))
class DeliveryHistory {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
  @Column(nullable=false) public Long taskId;
  @Enumerated(EnumType.STRING) public DeliveryStatus status;
  public String note; public LocalDateTime at = LocalDateTime.now();
}

@Entity @Table(name="volunteer_profiles", uniqueConstraints=@UniqueConstraint(columnNames="userId"))
class VolunteerProfile {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
  @Column(nullable=false) public Long userId;
  @Column(nullable=false) public String phone; @Column(nullable=false) public String vehicle; @Column(nullable=false) public String area;
}

@Entity @Table(name="notifications", indexes=@Index(columnList="userId"))
class Notification {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
  @Column(nullable=false) public Long userId;
  @Column(nullable=false, length=300) public String message;
  public boolean seen = false;
  public LocalDateTime at = LocalDateTime.now();
}

record VolunteerRequest(@NotBlank @Size(max=20) String phone, @NotBlank @Size(max=40) String vehicle, @NotBlank @Size(max=120) String area) {}
record DeliveryStatusRequest(@NotNull DeliveryStatus status) {}

record PageOf<T>(List<T> items, int page, int size, long total, int totalPages) {}
record ProfileRequest(@NotBlank @Size(max=80) String name) {}
record PasswordRequest(@NotBlank String currentPassword, @NotBlank @Size(min=8, max=72, message="Password must be 8-72 characters") String newPassword) {}
record SuspendRequest(boolean suspended, @Size(max=200) String reason) {}
