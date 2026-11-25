package com.renewiq.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

public enum BillingCycle {
    WEEKLY(52, 12),
    MONTHLY(1, 1),
    QUARTERLY(1, 3),
    YEARLY(1, 12);

    private final int numerator;
    private final int denominator;

    BillingCycle(int numerator, int denominator) {
        this.numerator = numerator;
        this.denominator = denominator;
    }

    /** Converts a per-cycle amount to its monthly equivalent. */
    public BigDecimal toMonthly(BigDecimal amount) {
        return amount.multiply(BigDecimal.valueOf(numerator))
                     .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
    }

    /** Next renewal date after one billing cycle. */
    public LocalDate next(LocalDate from) {
        return switch (this) {
            case WEEKLY -> from.plusWeeks(1);
            case MONTHLY -> from.plusMonths(1);
            case QUARTERLY -> from.plusMonths(3);
            case YEARLY -> from.plusYears(1);
        };
    }
}
