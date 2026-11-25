package com.renewiq.service;

import com.renewiq.dto.DashboardSummary;
import com.renewiq.model.Subscription;
import com.renewiq.model.SubscriptionStatus;
import com.renewiq.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@lombok.extern.slf4j.Slf4j
public class SubscriptionService {

    private final SubscriptionRepository repo;
    private final CurrentUser current;
    private final com.renewiq.repository.PaymentRepository payments;
    private final java.time.Clock clock;
    private final RenewalReminderDelivery reminders;

    public List<Subscription> findAll() {
        return repo.findByUserIdOrderByNextRenewalDateAsc(current.id());
    }

    public Subscription get(Long id) {
        return repo.findByIdAndUserId(id, current.id()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Subscription not found"));
    }

    public Subscription create(Subscription s) {
        s.setId(null);
        s.setUser(current.get());
        // Repository save commits before attempting SMTP in its separate transaction.
        // An email failure must not remove the user's newly added subscription.
        Subscription saved = repo.save(s);
        try { reminders.deliverOnCreation(saved.getId()); }
        catch (RuntimeException ex) {
            log.warn("Immediate renewal reminder failed for subscription {} ({})", saved.getId(), ex.getClass().getSimpleName());
        }
        return saved;
    }

    public Subscription update(Long id, Subscription in) {
        Subscription s = get(id);
        s.setName(in.getName());
        s.setCategory(in.getCategory());
        s.setAmount(in.getAmount());
        s.setBillingCycle(in.getBillingCycle());
        s.setNextRenewalDate(in.getNextRenewalDate());
        s.setStatus(in.getStatus());
        s.setNotes(in.getNotes());
        return repo.save(s);
    }

    public void delete(Long id) {
        repo.delete(get(id));
    }

    /** Marks the current cycle as paid and moves the renewal date forward. */
    @org.springframework.transaction.annotation.Transactional
    public Subscription renew(Long id, LocalDate expectedRenewalDate) {
        Subscription s = repo.lockByIdAndUserId(id, current.id()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Subscription not found"));
        if (expectedRenewalDate == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The renewal date is required. Refresh and try again.");
        if (!expectedRenewalDate.equals(s.getNextRenewalDate()) || payments.existsBySubscriptionIdAndRenewalDate(id, expectedRenewalDate)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This renewal has already been recorded or changed. Refresh your subscriptions.");
        }
        if (s.getStatus() != SubscriptionStatus.ACTIVE) throw new ResponseStatusException(HttpStatus.CONFLICT, "Only active subscriptions can be marked paid.");
        var payment = new com.renewiq.model.Payment();
        payment.setUser(s.getUser()); payment.setSubscriptionId(s.getId());
        payment.setSubscriptionName(s.getName()); payment.setCategory(s.getCategory());
        payment.setAmount(s.getAmount()); payment.setCurrency("INR"); payment.setBillingCycle(s.getBillingCycle());
        payment.setRenewalDate(s.getNextRenewalDate());
        payment.setPaidAt(java.time.LocalDateTime.ofInstant(clock.instant(), java.time.ZoneOffset.UTC));
        payments.saveAndFlush(payment);
        s.setNextRenewalDate(s.getBillingCycle().next(s.getNextRenewalDate()));
        return repo.save(s);
    }

    public Subscription setStatus(Long id, SubscriptionStatus status) {
        Subscription s = get(id);
        s.setStatus(status);
        return repo.save(s);
    }

    /** Active subscriptions due within the next N days (overdue ones included). */
    public List<Subscription> upcoming(int days) {
        return repo.findByUserIdAndStatusAndNextRenewalDateLessThanEqualOrderByNextRenewalDateAsc(
                current.id(), SubscriptionStatus.ACTIVE, LocalDate.now().plusDays(Math.max(1, Math.min(days, 365))));
    }

    public DashboardSummary summary() {
        List<Subscription> active = repo.findByUserIdAndStatus(current.id(), SubscriptionStatus.ACTIVE);

        BigDecimal monthly = active.stream()
                .map(s -> s.getBillingCycle().toMonthly(s.getAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        LocalDate weekEnd = LocalDate.now().plusDays(7);
        List<Subscription> dueWeek = active.stream()
                .filter(s -> !s.getNextRenewalDate().isAfter(weekEnd))
                .toList();
        BigDecimal dueAmount = dueWeek.stream()
                .map(Subscription::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, BigDecimal> byCategory = active.stream().collect(Collectors.groupingBy(
                Subscription::getCategory,
                LinkedHashMap::new,
                Collectors.reducing(BigDecimal.ZERO,
                        s -> s.getBillingCycle().toMonthly(s.getAmount()),
                        BigDecimal::add)));

        return new DashboardSummary(
                monthly,
                monthly.multiply(BigDecimal.valueOf(12)),
                active.size(),
                dueWeek.size(),
                dueAmount,
                byCategory);
    }
}
