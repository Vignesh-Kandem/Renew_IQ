package com.renewiq.dto;

import java.math.BigDecimal;
import java.util.Map;

public record DashboardSummary(
        BigDecimal monthlySpend,
        BigDecimal yearlyProjection,
        long activeCount,
        long dueThisWeekCount,
        BigDecimal dueThisWeekAmount,
        Map<String, BigDecimal> categoryBreakdown) {
}
