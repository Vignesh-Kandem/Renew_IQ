package com.renewiq.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.*;

@Entity @Table(name = "renewal_email_deliveries", uniqueConstraints =
    @UniqueConstraint(name = "uk_renewal_email", columnNames = {"subscription_id", "renewal_date", "days_before"}))
@Getter @Setter @NoArgsConstructor
public class RenewalEmailDelivery {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subscription_id", nullable = false)
    private Subscription subscription;
    @Column(nullable = false) private LocalDate renewalDate;
    @Column(nullable = false) private int daysBefore;
    @Column(nullable = false) private LocalDateTime sentAt;
}
