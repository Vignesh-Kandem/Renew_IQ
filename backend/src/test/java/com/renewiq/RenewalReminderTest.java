package com.renewiq;

import com.renewiq.model.*;
import com.renewiq.repository.*;
import com.renewiq.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:reminders;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop", "renewiq.reminders.email-enabled=false"
})
@AutoConfigureMockMvc
class RenewalReminderTest {
    @Autowired SubscriptionRepository subscriptions;
    @Autowired UserRepository users;
    @Autowired RenewalEmailDeliveryRepository deliveries;
    @Autowired RenewalReminderDelivery delivery;
    @Autowired RenewalReminderJob job;
    @Autowired MockMvc mvc;
    @MockBean RenewalEmailSender email;
    @MockBean Clock clock;
    private final LocalDate today = LocalDate.of(2026, 11, 17);
    @BeforeEach void setup() {
        deliveries.deleteAll(); subscriptions.deleteAll(); users.deleteAll();
        when(clock.getZone()).thenReturn(ZoneId.of("Asia/Kolkata"));
        when(clock.instant()).thenReturn(Instant.parse("2026-11-17T03:30:00Z"));
        when(email.enabled()).thenReturn(true);
    }
    private AppUser account() {
        var owner = new AppUser(); owner.setName("Reminder user");
        owner.setEmail(UUID.randomUUID() + "@example.com"); owner.setPasswordHash("test-hash"); owner.setEnabled(true);
        return users.saveAndFlush(owner);
    }
    private Subscription plan(AppUser owner, int days, SubscriptionStatus status) {
        var plan = new Subscription(); plan.setUser(owner); plan.setName("Music"); plan.setCategory("Entertainment");
        plan.setAmount(new BigDecimal("299.00")); plan.setBillingCycle(BillingCycle.MONTHLY);
        plan.setNextRenewalDate(today.plusDays(days)); plan.setStatus(status);
        return subscriptions.saveAndFlush(plan);
    }
    @Test void sendsOnlySevenThreeAndOneDayRemindersAndDeduplicatesRepeatedRuns() {
        var owner = account();
        for (int days : new int[]{7, 3, 1, 0, -1, 2, 8}) plan(owner, days, SubscriptionStatus.ACTIVE);
        plan(owner, 3, SubscriptionStatus.PAUSED); plan(owner, 1, SubscriptionStatus.CANCELLED);
        var disabled = account(); disabled.setEnabled(false); users.saveAndFlush(disabled);
        plan(disabled, 7, SubscriptionStatus.ACTIVE);
        job.run(); job.run();
        assertEquals(3, deliveries.count());
        verify(email, times(1)).send(any(), eq(7)); verify(email, times(1)).send(any(), eq(3));
        verify(email, times(1)).send(any(), eq(1)); verify(email, times(3)).send(any(), anyInt());
    }
    @Test void failureDoesNotRecordSuccessOrPreventOtherDeliveriesAndCanRetry() {
        var owner = account(); plan(owner, 3, SubscriptionStatus.ACTIVE); plan(owner, 1, SubscriptionStatus.ACTIVE);
        doThrow(new org.springframework.mail.MailSendException("Test SMTP failure")).when(email).send(any(), eq(3));
        job.run(); assertEquals(1, deliveries.count());
        doNothing().when(email).send(any(), eq(3));
        job.run(); assertEquals(2, deliveries.count());
        verify(email, times(1)).send(any(), eq(1)); verify(email, times(2)).send(any(), eq(3));
    }
    @Test void changedRenewalGetsNewReminderAndChangedStatusIsRechecked() {
        var plan = plan(account(), 7, SubscriptionStatus.ACTIVE);
        delivery.deliver(plan.getId());
        plan.setNextRenewalDate(today.plusDays(3)); subscriptions.saveAndFlush(plan);
        delivery.deliver(plan.getId()); assertEquals(2, deliveries.count());
        plan.setNextRenewalDate(today.plusDays(1)); plan.setStatus(SubscriptionStatus.PAUSED); subscriptions.saveAndFlush(plan);
        delivery.deliver(plan.getId()); assertEquals(2, deliveries.count());
    }
    @Test void disabledEmailDoesNotSendOrMarkDelivered() {
        var plan = plan(account(), 7, SubscriptionStatus.ACTIVE);
        when(email.enabled()).thenReturn(false); job.run(); delivery.deliver(plan.getId());
        assertEquals(0, deliveries.count()); verify(email, never()).send(any(), anyInt());
    }
    @Test void bellRequiresLoginAndOnlyShowsCurrentOwnersActiveUpcomingRenewals() throws Exception {
        var owner = account(); var other = account();
        plan(owner, 7, SubscriptionStatus.ACTIVE); plan(owner, 0, SubscriptionStatus.ACTIVE);
        plan(owner, -1, SubscriptionStatus.ACTIVE); plan(owner, 8, SubscriptionStatus.ACTIVE);
        plan(owner, 1, SubscriptionStatus.PAUSED); plan(owner, 3, SubscriptionStatus.CANCELLED);
        plan(other, 1, SubscriptionStatus.ACTIVE);
        mvc.perform(get("/api/notifications")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/notifications").with(user(owner.getEmail())))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].daysUntil").value(0)).andExpect(jsonPath("$[1].daysUntil").value(7));
        mvc.perform(get("/api/notifications").with(user(other.getEmail())))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].daysUntil").value(1));
    }

    private long addThroughApp(AppUser owner, int days, SubscriptionStatus status) throws Exception {
        var result = mvc.perform(post("/api/subscriptions").with(user(owner.getEmail())).with(csrf())
            .contentType("application/json").content("{\"name\":\"YouTube\",\"category\":\"Entertainment\",\"amount\":299,\"billingCycle\":\"MONTHLY\",\"nextRenewalDate\":\""
                + today.plusDays(days) + "\",\"status\":\"" + status + "\"}"))
            .andExpect(status().isCreated()).andReturn();
        return new com.fasterxml.jackson.databind.ObjectMapper().readTree(result.getResponse().getContentAsString()).path("id").asLong();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {0, 1, 2, 3, 4, 5, 6, 7})
    void addingSubscriptionWithinAWeekSendsImmediatelyAndUpdatesBell(int days) throws Exception {
        var owner = account();
        long id = addThroughApp(owner, days, SubscriptionStatus.ACTIVE);
        verify(email, times(1)).send(argThat(s -> s.getId().equals(id)
            && s.getUser().getEmail().equals(owner.getEmail())), eq(days));
        assertTrue(deliveries.existsBySubscriptionIdAndRenewalDateAndDaysBefore(id, today.plusDays(days), days));
        delivery.deliverOnCreation(id); job.run();
        verify(email, times(1)).send(any(), anyInt());
        mvc.perform(get("/api/notifications").with(user(owner.getEmail())))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].name").value("YouTube")).andExpect(jsonPath("$[0].daysUntil").value(days));
    }

    @Test void addingOverdueDistantOrInactiveSubscriptionDoesNotEmail() throws Exception {
        var owner = account();
        addThroughApp(owner, -1, SubscriptionStatus.ACTIVE); addThroughApp(owner, 8, SubscriptionStatus.ACTIVE);
        addThroughApp(owner, 1, SubscriptionStatus.PAUSED); addThroughApp(owner, 2, SubscriptionStatus.CANCELLED);
        verify(email, never()).send(any(), anyInt()); assertEquals(0, deliveries.count());
    }

    @Test void immediateReminderIsFollowedByThreeAndOneDayScheduledReminders() throws Exception {
        var owner = account(); addThroughApp(owner, 5, SubscriptionStatus.ACTIVE);
        verify(email).send(any(), eq(5));
        when(clock.instant()).thenReturn(Instant.parse("2026-11-19T03:30:00Z")); job.run(); job.run();
        when(clock.instant()).thenReturn(Instant.parse("2026-11-21T03:30:00Z")); job.run(); job.run();
        verify(email, times(1)).send(any(), eq(3)); verify(email, times(1)).send(any(), eq(1));
        assertEquals(3, deliveries.count());
    }

    @Test void dayAfterTomorrowGetsImmediateEmailAndNextDaysOneDayReminder() throws Exception {
        addThroughApp(account(), 2, SubscriptionStatus.ACTIVE);
        when(clock.instant()).thenReturn(Instant.parse("2026-11-18T03:30:00Z")); job.run();
        verify(email).send(any(), eq(2)); verify(email).send(any(), eq(1)); assertEquals(2, deliveries.count());
    }

    @Test void failedImmediateSmtpDoesNotLoseSubscriptionOrMarkEmailDelivered() throws Exception {
        var owner = account();
        doThrow(new org.springframework.mail.MailSendException("Test SMTP failure")).when(email).send(any(), anyInt());
        long id = addThroughApp(owner, 1, SubscriptionStatus.ACTIVE);
        assertTrue(subscriptions.existsById(id)); assertEquals(0, deliveries.count());
        doNothing().when(email).send(any(), anyInt()); job.run(); assertEquals(1, deliveries.count());
    }

    @Test void disabledSmtpStillCreatesSubscriptionAndBellAlert() throws Exception {
        when(email.enabled()).thenReturn(false); var owner = account();
        addThroughApp(owner, 2, SubscriptionStatus.ACTIVE);
        verify(email, never()).send(any(), anyInt());
        mvc.perform(get("/api/notifications").with(user(owner.getEmail())))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].daysUntil").value(2));
    }
}
