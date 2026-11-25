package com.renewiq;

import com.fasterxml.jackson.databind.*;
import com.renewiq.model.*;
import com.renewiq.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:payments;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop", "renewiq.reminders.email-enabled=false"
})
@AutoConfigureMockMvc
class PaymentHistoryTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @SpyBean SubscriptionRepository subscriptions;
    @Autowired PaymentRepository payments;
    private AppUser owner;
    @BeforeEach void setup() {
        owner = account();
    }
    private AppUser account() {
        var user = new AppUser(); user.setName("Payment user"); user.setEmail(UUID.randomUUID() + "@example.com");
        user.setPasswordHash("test-hash"); user.setEnabled(true); return users.saveAndFlush(user);
    }
    private Subscription plan(BillingCycle cycle) {
        var s = new Subscription(); s.setUser(owner); s.setName("Original plan"); s.setCategory("Music");
        s.setAmount(new BigDecimal("299.99")); s.setBillingCycle(cycle);
        s.setNextRenewalDate(LocalDate.of(2028, 1, 31)); s.setStatus(SubscriptionStatus.ACTIVE);
        return subscriptions.saveAndFlush(s);
    }
    private org.springframework.test.web.servlet.ResultActions renew(Subscription s, AppUser user) throws Exception {
        return mvc.perform(post("/api/subscriptions/" + s.getId() + "/renew").with(user(user.getEmail())).with(csrf())
            .contentType("application/json").content("{\"expectedRenewalDate\":\"" + s.getNextRenewalDate() + "\"}"));
    }
    private JsonNode history(AppUser user) throws Exception {
        var result = mvc.perform(get("/api/payments").with(user(user.getEmail())))
            .andExpect(status().isOk()).andReturn();
        return json.readTree(result.getResponse().getContentAsString());
    }
    @ParameterizedTest @EnumSource(BillingCycle.class)
    void recordsExactCycleAmountAndAdvancesRenewalOnce(BillingCycle cycle) throws Exception {
        var s = plan(cycle);
        renew(s, owner).andExpect(status().isOk())
            .andExpect(jsonPath("nextRenewalDate").value(cycle.next(s.getNextRenewalDate()).toString()));
        var rows = history(owner).path("items"); assertEquals(1, rows.size());
        assertEquals("Original plan", rows.get(0).path("subscriptionName").asText());
        assertEquals(new BigDecimal("299.99"), rows.get(0).path("amount").decimalValue());
        assertEquals("INR", rows.get(0).path("currency").asText());
        assertEquals("2028-01-31", rows.get(0).path("renewalDate").asText());
        assertTrue(rows.get(0).path("recordedAt").asText().endsWith("Z"));
        assertFalse(rows.get(0).has("user"));
        renew(s, owner).andExpect(status().isConflict());
        assertEquals(1, history(owner).path("totalElements").asInt());
        assertEquals(cycle.next(s.getNextRenewalDate()), subscriptions.findById(s.getId()).orElseThrow().getNextRenewalDate());
    }
    @Test void historyKeepsSnapshotsAfterEditingAndDeletingSubscription() throws Exception {
        var s = plan(BillingCycle.MONTHLY); renew(s, owner).andExpect(status().isOk());
        mvc.perform(put("/api/subscriptions/" + s.getId()).with(user(owner.getEmail())).with(csrf())
            .contentType("application/json").content("{\"name\":\"Changed\",\"category\":\"Other\",\"amount\":999,\"billingCycle\":\"YEARLY\",\"nextRenewalDate\":\"2028-03-01\",\"status\":\"ACTIVE\"}"))
            .andExpect(status().isOk());
        mvc.perform(delete("/api/subscriptions/" + s.getId()).with(user(owner.getEmail())).with(csrf())).andExpect(status().isNoContent());
        var row = history(owner).path("items").get(0);
        assertEquals("Original plan", row.path("subscriptionName").asText());
        assertEquals("Music", row.path("category").asText());
        assertEquals(new BigDecimal("299.99"), row.path("amount").decimalValue());
        assertEquals("MONTHLY", row.path("billingCycle").asText());
    }
    @Test void authenticationOwnershipStatusAndCsrfProtectPayments() throws Exception {
        var s = plan(BillingCycle.MONTHLY); var other = account();
        mvc.perform(get("/api/payments")).andExpect(status().isUnauthorized());
        renew(s, other).andExpect(status().isNotFound());
        mvc.perform(post("/api/subscriptions/" + s.getId() + "/renew").with(user(owner.getEmail())))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/subscriptions/" + s.getId() + "/renew").with(user(owner.getEmail())).with(csrf()))
            .andExpect(status().isBadRequest());
        s.setStatus(SubscriptionStatus.PAUSED); subscriptions.saveAndFlush(s);
        renew(s, owner).andExpect(status().isConflict());
        assertEquals(0, history(owner).path("totalElements").asInt());
        s.setStatus(SubscriptionStatus.ACTIVE); subscriptions.saveAndFlush(s);
        renew(s, owner).andExpect(status().isOk());
        assertEquals(0, history(other).path("totalElements").asInt());
    }
    @Test void paymentAndRenewalRollBackTogetherWhenSubscriptionSaveFails() throws Exception {
        var s = plan(BillingCycle.MONTHLY);
        doThrow(new IllegalStateException("Simulated database failure")).when(subscriptions).save(any(Subscription.class));
        assertThrows(Exception.class, () -> renew(s, owner));
        assertEquals(0, history(owner).path("totalElements").asInt());
        assertEquals(s.getNextRenewalDate(), subscriptions.findById(s.getId()).orElseThrow().getNextRenewalDate());
    }
    @Test void historyIsPaginatedAndNewestFirst() throws Exception {
        var s = plan(BillingCycle.WEEKLY);
        for (int i = 0; i < 21; i++) {
            renew(s, owner).andExpect(status().isOk());
            s.setNextRenewalDate(s.getBillingCycle().next(s.getNextRenewalDate()));
        }
        var first = history(owner);
        assertEquals(21, first.path("totalElements").asInt()); assertEquals(2, first.path("totalPages").asInt());
        assertEquals(20, first.path("items").size());
        assertTrue(first.path("items").get(0).path("id").asLong() > first.path("items").get(1).path("id").asLong());
        mvc.perform(get("/api/payments?page=1").with(user(owner.getEmail())))
            .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1));
    }
    @Test void onlyOwnerCanDeletePaymentAndRenewalRemainsUnchanged() throws Exception {
        var s = plan(BillingCycle.MONTHLY); renew(s, owner).andExpect(status().isOk());
        long paymentId = history(owner).path("items").get(0).path("id").asLong();
        var other = account();
        mvc.perform(delete("/api/payments/" + paymentId).with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/payments/" + paymentId).with(user(owner.getEmail()))).andExpect(status().isForbidden());
        mvc.perform(delete("/api/payments/" + paymentId).with(user(other.getEmail())).with(csrf())).andExpect(status().isNotFound());
        assertEquals(1, history(owner).path("totalElements").asInt());
        mvc.perform(delete("/api/payments/" + paymentId).with(user(owner.getEmail())).with(csrf())).andExpect(status().isNoContent());
        assertEquals(0, history(owner).path("totalElements").asInt());
        assertEquals(s.getBillingCycle().next(s.getNextRenewalDate()), subscriptions.findById(s.getId()).orElseThrow().getNextRenewalDate());
        mvc.perform(delete("/api/payments/" + paymentId).with(user(owner.getEmail())).with(csrf())).andExpect(status().isNotFound());
    }
}
