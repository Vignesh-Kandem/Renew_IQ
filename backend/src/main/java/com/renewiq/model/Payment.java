package com.renewiq.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.*;

@Entity @Table(name = "payments", uniqueConstraints = @UniqueConstraint(
    name = "uk_payment_subscription_cycle", columnNames = {"subscription_id", "renewal_date"}))
@Getter @Setter @NoArgsConstructor
public class Payment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @JsonIgnore @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false) private AppUser user;
    @Column(nullable = false) private Long subscriptionId;
    @Column(nullable = false, length = 100) private String subscriptionName;
    @Column(nullable = false, length = 50) private String category;
    @Column(nullable = false, precision = 10, scale = 2) private BigDecimal amount;
    @Column(nullable = false, length = 3) private String currency;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private BillingCycle billingCycle;
    @Column(nullable = false) private LocalDate renewalDate;
    @JsonIgnore @Column(nullable = false) private LocalDateTime paidAt;
    // Explicit UTC offset so browsers render the recorded time correctly.
    public Instant getRecordedAt() { return paidAt.toInstant(ZoneOffset.UTC); }
}
