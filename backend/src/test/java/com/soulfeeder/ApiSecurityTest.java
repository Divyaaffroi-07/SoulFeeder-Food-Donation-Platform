package com.soulfeeder;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Full-stack tests through the real security filter chain, against an in-memory H2 database. */
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class ApiSecurityTest {
  @Autowired MockMvc mvc; @Autowired ObjectMapper om;
  static final MediaType JSON = MediaType.APPLICATION_JSON;

  String email(){ return UUID.randomUUID().toString().substring(0, 8) + "@test.com"; }
  String body(String role, String email) throws Exception { return om.writeValueAsString(Map.of("name", "Tester", "email", email, "password", "password123", "role", role)); }
  String register(String role) throws Exception {
    String res = mvc.perform(post("/api/auth/register").contentType(JSON).content(body(role, email()))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    return om.readTree(res).get("token").asText(); }
  String donationJson() throws Exception {
    return om.writeValueAsString(Map.of("title", "Rice", "foodType", "Cooked meal", "quantity", 10, "pickupLocation", "Salem",
      "preparedAt", LocalDateTime.now().minusHours(1).toString(), "expiresAt", LocalDateTime.now().plusHours(5).toString())); }
  long postDonation(String donorToken) throws Exception {
    String res = mvc.perform(post("/api/donations").header("Authorization", "Bearer " + donorToken).contentType(JSON).content(donationJson())).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    return om.readTree(res).get("id").asLong(); }

  @Test void protectedEndpointNeedsToken() throws Exception { mvc.perform(get("/api/donations")).andExpect(status().isUnauthorized()); }
  @Test void tamperedTokenIsRejected() throws Exception { mvc.perform(get("/api/donations").header("Authorization", "Bearer not.a.jwt")).andExpect(status().isUnauthorized()); }
  @Test void statsArePublic() throws Exception { mvc.perform(get("/api/stats")).andExpect(status().isOk()); }
  @Test void adminCannotBeSelfRegistered() throws Exception { mvc.perform(post("/api/auth/register").contentType(JSON).content(body("ADMIN", email()))).andExpect(status().isForbidden()); }
  @Test void duplicateEmailIsConflict() throws Exception { String e = email();
    mvc.perform(post("/api/auth/register").contentType(JSON).content(body("DONOR", e))).andExpect(status().isCreated());
    mvc.perform(post("/api/auth/register").contentType(JSON).content(body("DONOR", e))).andExpect(status().isConflict()); }
  @Test void passwordHashIsNeverReturned() throws Exception {
    String res = mvc.perform(post("/api/auth/register").contentType(JSON).content(body("DONOR", email()))).andReturn().getResponse().getContentAsString();
    assertThat(res).doesNotContain("passwordHash").doesNotContain("password123"); }
  @Test void wrongPasswordIsUnauthorizedWithGenericMessage() throws Exception { String e = email();
    mvc.perform(post("/api/auth/register").contentType(JSON).content(body("DONOR", e)));
    mvc.perform(post("/api/auth/login").contentType(JSON).content(om.writeValueAsString(Map.of("email", e, "password", "wrong-password")))).andExpect(status().isUnauthorized()); }
  @Test void ngoCannotPostDonations() throws Exception {
    mvc.perform(post("/api/donations").header("Authorization", "Bearer " + register("NGO")).contentType(JSON).content(donationJson())).andExpect(status().isForbidden()); }
  @Test void unverifiedNgoCannotClaim() throws Exception { long id = postDonation(register("DONOR"));
    mvc.perform(post("/api/donations/" + id + "/claim").header("Authorization", "Bearer " + register("NGO"))).andExpect(status().isForbidden()); }
  @Test void donorCannotClaim() throws Exception { String donor = register("DONOR"); long id = postDonation(donor);
    mvc.perform(post("/api/donations/" + id + "/claim").header("Authorization", "Bearer " + donor)).andExpect(status().isForbidden()); }
  @Test void volunteerCannotUseAdminApi() throws Exception { mvc.perform(get("/api/admin/dashboard").header("Authorization", "Bearer " + register("VOLUNTEER"))).andExpect(status().isForbidden()); }
  @Test void invalidDonationReturnsFieldErrors() throws Exception {
    mvc.perform(post("/api/donations").header("Authorization", "Bearer " + register("DONOR")).contentType(JSON).content("{\"title\":\"\",\"quantity\":0}")).andExpect(status().isBadRequest()); }
}
