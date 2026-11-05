package com.stayease.reservation.repository;

import com.stayease.reservation.entity.PaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {

    Optional<PaymentTransaction> findByIdempotencyKey(String idempotencyKey);

    List<PaymentTransaction> findByReservationId(Long reservationId);

    Optional<PaymentTransaction> findByTransactionReference(String transactionReference);
}
