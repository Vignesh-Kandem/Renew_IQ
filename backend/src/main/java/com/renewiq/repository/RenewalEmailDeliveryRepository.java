package com.renewiq.repository;

import com.renewiq.model.RenewalEmailDelivery;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;

public interface RenewalEmailDeliveryRepository extends JpaRepository<RenewalEmailDelivery, Long> {
    @org.springframework.data.jpa.repository.Query("select count(d) > 0 from RenewalEmailDelivery d where d.subscription.id = :id and d.renewalDate = :date and d.daysBefore = :days")
    boolean existsBySubscriptionIdAndRenewalDateAndDaysBefore(
        @org.springframework.data.repository.query.Param("id") Long subscriptionId,
        @org.springframework.data.repository.query.Param("date") LocalDate renewalDate,
        @org.springframework.data.repository.query.Param("days") int daysBefore);
}
