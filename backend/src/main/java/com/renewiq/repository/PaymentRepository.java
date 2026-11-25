package com.renewiq.repository;

import com.renewiq.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.*;
import java.time.LocalDate;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("delete from Payment p where p.id = :id and p.user.id = :userId")
    int deleteOwned(@org.springframework.data.repository.query.Param("id") Long id,
        @org.springframework.data.repository.query.Param("userId") Long userId);
    Page<Payment> findByUserIdOrderByIdDesc(Long userId, Pageable pageable);
    boolean existsBySubscriptionIdAndRenewalDate(Long subscriptionId, LocalDate renewalDate);
}
