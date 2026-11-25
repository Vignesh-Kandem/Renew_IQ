package com.renewiq.controller;

import com.renewiq.model.SubscriptionStatus;
import com.renewiq.repository.SubscriptionRepository;
import com.renewiq.service.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.List;

@RestController @RequestMapping("/api/notifications") @RequiredArgsConstructor
public class NotificationController {
    private final SubscriptionRepository subscriptions;
    private final CurrentUser current;
    private final Clock clock;
    public record RenewalAlert(Long subscriptionId, String name, BigDecimal amount, LocalDate renewalDate, long daysUntil) { }
    @GetMapping
    public List<RenewalAlert> list() {
        LocalDate today = LocalDate.now(clock);
        return subscriptions.findByUserIdAndStatusAndNextRenewalDateLessThanEqualOrderByNextRenewalDateAsc(
                current.id(), SubscriptionStatus.ACTIVE, today.plusDays(7)).stream()
                .filter(s -> !s.getNextRenewalDate().isBefore(today))
                .map(s -> new RenewalAlert(s.getId(), s.getName(), s.getAmount(), s.getNextRenewalDate(),
                    ChronoUnit.DAYS.between(today, s.getNextRenewalDate()))).toList();
    }
}
