package com.stayease.review.repository;

import com.stayease.review.entity.ReviewAuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReviewAuditEventRepository extends JpaRepository<ReviewAuditEvent, Long>, JpaSpecificationExecutor<ReviewAuditEvent> {

    List<ReviewAuditEvent> findByEntityIdOrderByTimestampDesc(String entityId);
}
