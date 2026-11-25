package com.renewiq.service;

import com.renewiq.model.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AssistantAnalyticsTest {
    private final SubscriptionService subs = mock(SubscriptionService.class);
    private final AssistantAnalytics analytics = new AssistantAnalytics(subs);
    @Test void normalizesCyclesAndExcludesInactiveSubscriptions() {
        var monthly = plan(1, "Monthly", "Music", "100", BillingCycle.MONTHLY);
        var yearly = plan(2, "Yearly", "Music", "1200", BillingCycle.YEARLY);
        var weekly = plan(3, "Weekly", "Health", "12", BillingCycle.WEEKLY);
        var paused = plan(4, "Paused", "Music", "999", BillingCycle.MONTHLY); paused.setStatus(SubscriptionStatus.PAUSED);
        when(subs.findAll()).thenReturn(List.of(monthly, yearly, weekly, paused));
        var chart = analytics.chart("categories", 30, 6, "");
        assertEquals(2, chart.points().size());
        assertEquals("Music", chart.points().get(0).label());
        assertEquals(0, new BigDecimal("200").compareTo(chart.points().get(0).value()));
        assertEquals(0, new BigDecimal("52").compareTo(chart.points().get(1).value()));
        assertEquals(1, analytics.chart("categories", 30, 6, "music").points().size());
        assertTrue(analytics.chart("categories", 30, 6, "Unknown").points().isEmpty());
    }
    @Test void topItemsPreserveTotalThroughOtherBucketAndUniqueIds() {
        List<Subscription> plans = new ArrayList<>();
        for (int i=1; i<=15; i++) plans.add(plan(i, "Same name", "Category " + i, "10", BillingCycle.MONTHLY));
        when(subs.findAll()).thenReturn(plans);
        var chart = analytics.chart("subscriptions", 30, 6, "");
        assertEquals(12, chart.points().size());
        assertEquals("Other (remaining items)", chart.points().get(11).label());
        assertEquals(0, new BigDecimal("150").compareTo(chart.points().stream().map(p -> p.value()).reduce(BigDecimal.ZERO, BigDecimal::add)));
    }
    @Test void renewalBinsAreChronologicalAndExcludeOverdueAndOutsideWindow() {
        var today = plan(1, "Today", "Test", "50", BillingCycle.YEARLY);
        var tomorrow = plan(2, "Tomorrow", "Test", "100", BillingCycle.MONTHLY); tomorrow.setNextRenewalDate(LocalDate.now().plusDays(1));
        var overdue = plan(3, "Overdue", "Test", "999", BillingCycle.MONTHLY); overdue.setNextRenewalDate(LocalDate.now().minusDays(1));
        var later = plan(4, "Later", "Test", "999", BillingCycle.MONTHLY); later.setNextRenewalDate(LocalDate.now().plusDays(31));
        when(subs.findAll()).thenReturn(List.of(later, overdue, tomorrow, today));
        var chart = analytics.chart("renewals", 30, 6, "");
        assertTrue(chart.points().size() <= 12);
        assertEquals(LocalDate.now().toString(), chart.points().get(0).label());
        assertEquals(0, new BigDecimal("150").compareTo(chart.points().stream().map(p -> p.value()).reduce(BigDecimal.ZERO, BigDecimal::add)));
        assertTrue(chart.note().contains("Overdue dates are excluded"));
    }
    @Test void projectionsAreCumulativeEstimatesAndEmptyDatasetsRemainEmpty() {
        when(subs.findAll()).thenReturn(List.of(plan(1, "Plan", "Test", "100", BillingCycle.MONTHLY)));
        var chart = analytics.chart("projection", 30, 3, "");
        assertEquals(3, chart.points().size());
        assertEquals(0, new BigDecimal("300").compareTo(chart.points().get(2).value()));
        assertTrue(chart.note().contains("not historical"));
        assertThrows(IllegalArgumentException.class, () -> analytics.chart("history", 30, 3, ""));
        when(subs.findAll()).thenReturn(List.of());
        assertTrue(analytics.chart("projection", 30, 3, "").points().isEmpty());
    }
    private Subscription plan(long id, String name, String category, String amount, BillingCycle cycle) {
        var s = new Subscription(); s.setId(id); s.setName(name); s.setCategory(category);
        s.setAmount(new BigDecimal(amount)); s.setBillingCycle(cycle); s.setStatus(SubscriptionStatus.ACTIVE);
        s.setNextRenewalDate(LocalDate.now()); return s;
    }
}
