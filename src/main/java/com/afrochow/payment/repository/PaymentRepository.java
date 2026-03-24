package com.afrochow.payment.repository;
import com.afrochow.payment.model.Payment;
import com.afrochow.order.model.Order;
import com.afrochow.common.enums.PaymentStatus;
import com.afrochow.common.enums.PaymentMethod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.List;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByOrder(Order order);

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
