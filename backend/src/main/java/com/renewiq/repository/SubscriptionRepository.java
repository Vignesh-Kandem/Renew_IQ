package com.renewiq.repository;

import com.renewiq.model.Subscription;
import com.renewiq.model.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select s from Subscription s where s.id = :id and s.user.id = :userId")
    java.util.Optional<Subscription> lockByIdAndUserId(@org.springframework.data.repository.query.Param("id") Long id,
        @org.springframework.data.repository.query.Param("userId") Long userId);
    @org.springframework.data.jpa.repository.Query("select s.id from Subscription s join s.user u where s.status = com.renewiq.model.SubscriptionStatus.ACTIVE and u.enabled = true and s.nextRenewalDate in :dates")
    List<Long> findReminderCandidates(@org.springframework.data.repository.query.Param("dates") List<LocalDate> dates);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select s from Subscription s join fetch s.user where s.id = :id")
    java.util.Optional<Subscription> lockForReminder(@org.springframework.data.repository.query.Param("id") Long id);
    List<Subscription> findByUserIdOrderByNextRenewalDateAsc(Long userId);
    java.util.Optional<Subscription> findByIdAndUserId(Long id, Long userId);

    List<Subscription> findByUserIdAndStatus(Long userId, SubscriptionStatus status);

    List<Subscription> findByUserIdAndStatusAndNextRenewalDateLessThanEqualOrderByNextRenewalDateAsc(
            Long userId, SubscriptionStatus status, LocalDate date);
}
