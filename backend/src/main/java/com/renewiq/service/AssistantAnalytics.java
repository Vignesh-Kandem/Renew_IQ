package com.renewiq.service;

import com.renewiq.dto.AssistantChart;
import com.renewiq.dto.AssistantChart.Point;
import com.renewiq.model.Subscription;
import com.renewiq.model.SubscriptionStatus;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AssistantAnalytics {
    private final SubscriptionService subscriptions;
    public AssistantAnalytics(SubscriptionService subscriptions) { this.subscriptions = subscriptions; }

    public AssistantChart chart(String metric, int days, int months, String category) {
        List<Subscription> active = subscriptions.findAll().stream()
                .filter(s -> s.getStatus() == SubscriptionStatus.ACTIVE)
                .filter(s -> category == null || category.isBlank() || s.getCategory().equalsIgnoreCase(category.trim()))
                .toList();
        String scope = category == null || category.isBlank() ? "" : " · " + category.trim();
        String note = "Active subscriptions only. Monthly equivalents use weekly × 52/12, quarterly ÷ 3 and yearly ÷ 12. Amounts in INR; this is a current snapshot, not payment history.";
        Map<String, BigDecimal> grouped = new TreeMap<>();
        switch (metric) {
            case "categories" -> {
                active.forEach(s -> grouped.merge(s.getCategory(), monthly(s), BigDecimal::add));
                return result("bar", "Monthly spending by category" + scope, "Monthly equivalent (INR)", "Category", note, ranked(grouped));
            }
            case "subscriptions" -> {
                active.forEach(s -> grouped.merge(s.getName() + " (#" + s.getId() + ")", monthly(s), BigDecimal::add));
                return result("bar", "Subscription cost comparison" + scope, "Monthly equivalent (INR)", "Subscription", note, ranked(grouped));
            }
            case "renewals" -> {
                int window = Math.max(1, Math.min(365, days));
                LocalDate today = LocalDate.now();
                active.stream().filter(s -> !s.getNextRenewalDate().isBefore(today) && !s.getNextRenewalDate().isAfter(today.plusDays(window)))
                        .forEach(s -> grouped.merge(s.getNextRenewalDate().toString(), s.getAmount(), BigDecimal::add));
                // At most twelve chronological bins; zero-value bins make gaps explicit.
                int binSize = Math.max(1, (int) Math.ceil((window + 1) / 12.0));
                List<Point> points = new ArrayList<>();
                for (int offset = 0; offset <= window; offset += binSize) {
                    LocalDate from = today.plusDays(offset), to = today.plusDays(Math.min(window, offset + binSize - 1));
                    BigDecimal sum = grouped.entrySet().stream().filter(e -> !LocalDate.parse(e.getKey()).isBefore(from) && !LocalDate.parse(e.getKey()).isAfter(to))
                            .map(Map.Entry::getValue).reduce(BigDecimal.ZERO, BigDecimal::add);
                    points.add(new Point(from.toString(), sum));
                }
                return result("line", "Upcoming renewals · next " + window + " days" + scope, "Renewal date / bin start", "Amount due (INR)",
                        "Each point sums the recorded next renewal in a " + binSize + "-day bin, including today. Overdue dates are excluded. Later recurring cycles are not projected. Active subscriptions only.", active.isEmpty() ? List.of() : points);
            }
            case "projection" -> {
                int count = Math.max(1, Math.min(12, months));
                BigDecimal monthly = active.stream().map(this::monthly).reduce(BigDecimal.ZERO, BigDecimal::add);
                List<Point> points = new ArrayList<>();
                for (int i = 1; i <= count && !active.isEmpty(); i++) points.add(new Point(YearMonth.now().plusMonths(i - 1).toString(), monthly.multiply(BigDecimal.valueOf(i))));
                return result("line", "Cumulative spending estimate" + scope, "Month (starting this month)", "Cumulative estimate (INR)",
                        "Projection, not historical spending or a payment schedule. Assumes today's active plans and monthly equivalent costs remain unchanged; excludes future price changes and cancellations.", points);
            }
            default -> throw new IllegalArgumentException("Choose categories, subscriptions, renewals or projection. Actual historical spending is unavailable because payment history is not recorded.");
        }
    }
    private BigDecimal monthly(Subscription s) { return s.getBillingCycle().toMonthly(s.getAmount()); }
    private List<Point> ranked(Map<String, BigDecimal> grouped) {
        List<Point> all = grouped.entrySet().stream().sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .map(e -> new Point(e.getKey(), e.getValue())).collect(Collectors.toCollection(ArrayList::new));
        if (all.size() <= 12) return all;
        BigDecimal rest = all.subList(11, all.size()).stream().map(Point::value).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<Point> limited = new ArrayList<>(all.subList(0, 11)); limited.add(new Point("Other (remaining items)", rest)); return limited;
    }
    private AssistantChart result(String type, String title, String x, String y, String note, List<Point> points) {
        return new AssistantChart(type, title, x, y, "INR", LocalDate.now(), note, points);
    }
}
