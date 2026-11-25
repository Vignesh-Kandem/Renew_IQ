package com.renewiq.service;

import com.renewiq.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import java.time.*;
import java.util.List;

@Component @RequiredArgsConstructor @Slf4j
public class RenewalReminderJob {
    private final SubscriptionRepository subscriptions;
    private final RenewalReminderDelivery delivery;
    private final RenewalEmailSender email;
    private final Clock clock;

    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(cron = "${renewiq.reminders.cron:0 0 9 * * *}", zone = "${renewiq.reminders.zone:Asia/Kolkata}")
    public void run() {
        if (!email.enabled()) return;
        LocalDate today = LocalDate.now(clock);
        for (Long id : subscriptions.findReminderCandidates(List.of(today.plusDays(7), today.plusDays(3), today.plusDays(1)))) {
            try { delivery.deliver(id); }
            catch (RuntimeException ex) {
                // No recipient address, credentials, or provider response in application logs.
                log.warn("Renewal reminder failed for subscription {}; eligible for retry on the same reminder date ({})", id, ex.getClass().getSimpleName());
            }
        }
    }
}
