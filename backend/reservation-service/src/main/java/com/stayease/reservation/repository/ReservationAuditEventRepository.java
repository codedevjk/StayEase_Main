package com.stayease.reservation.repository;

import com.stayease.reservation.entity.ReservationAuditEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReservationAuditEventRepository extends JpaRepository<ReservationAuditEvent, Long>, JpaSpecificationExecutor<ReservationAuditEvent> {

    List<ReservationAuditEvent> findByEntityIdOrderByTimestampDesc(String entityId);
}
