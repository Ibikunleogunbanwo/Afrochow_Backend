package com.afrochow.payment.repository;
import com.afrochow.payment.model.Payment;
import com.afrochow.order.model.Order;
import com.afrochow.common.enums.PaymentStatus;
import com.afrochow.common.enums.PaymentMethod;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.List;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByOrder(Order order);

    /**
     * Fetch the payment for an order with a database-level write lock.
     * Use this before any status transition (AUTHORIZED → COMPLETED, AUTHORIZED → CANCELLED,
     * COMPLETED → REFUNDED) to prevent two concurrent threads from both seeing the same
     * status and both attempting to charge / refund the same Stripe intent.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.order = :order")
    Optional<Payment> findByOrderWithLock(@Param("order") Order order);

    Optional<Payment> findByTransactionId(String transactionId);

    List<Payment> findByStatus(PaymentStatus status);

    List<Payment> findByPaymentMethod(PaymentMethod paymentMethod);

    List<Payment> findByStatusAndPaymentMethod(PaymentStatus status, PaymentMethod paymentMethod);


    @Query("SELECT p FROM Payment p WHERE p.status = 'FAILED' ORDER BY p.paymentTime DESC")
    List<Payment> findFailedPayments();

    Long countByStatus(PaymentStatus status);

    // Date-range payment count (used by AdminAnalytics date filter)
    @Query("SELECT COUNT(p) FROM Payment p WHERE p.status = :status AND p.paymentTime >= :startDate AND p.paymentTime <= :endDate")
    Long countByStatusAndPaymentTimeBetween(@Param("status") PaymentStatus status,
                                            @Param("startDate") LocalDateTime startDate,
                                            @Param("endDate") LocalDateTime endDate);
}
