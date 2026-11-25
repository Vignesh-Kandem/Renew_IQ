package com.renewiq.service;

import com.renewiq.model.*;
import com.renewiq.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.Set;

@Service @RequiredArgsConstructor
public class RenewalReminderDelivery {
    private final SubscriptionRepository subscriptions;
    private final RenewalEmailDeliveryRepository deliveries;
    private final RenewalEmailSender email;
    private final Clock clock;

    // Serialize concurrent job instances for this subscription, including the duplicate check.
    @Transactional
    public void deliver(Long id) {
        deliverEligible(id, false);
    }

    @Transactional
    public void deliverOnCreation(Long id) {
        deliverEligible(id, true);
    }

    private void deliverEligible(Long id, boolean newlyAdded) {
        if (!email.enabled()) return;
        var subscription = subscriptions.lockForReminder(id).orElse(null);
        if (subscription == null || subscription.getStatus() != SubscriptionStatus.ACTIVE
                || !subscription.getUser().isEnabled()) return;
        int days = (int) ChronoUnit.DAYS.between(LocalDate.now(clock), subscription.getNextRenewalDate());
        boolean eligible = newlyAdded ? days >= 0 && days <= 7 : Set.of(7, 3, 1).contains(days);
        if (!eligible || deliveries.existsBySubscriptionIdAndRenewalDateAndDaysBefore(
                id, subscription.getNextRenewalDate(), days)) return;
        email.send(subscription, days);
        var delivery = new RenewalEmailDelivery();
        delivery.setSubscription(subscription); delivery.setRenewalDate(subscription.getNextRenewalDate());
        delivery.setDaysBefore(days); delivery.setSentAt(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        deliveries.saveAndFlush(delivery);
    }
}
