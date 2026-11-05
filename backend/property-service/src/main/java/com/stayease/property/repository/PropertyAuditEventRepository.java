package com.stayease.property.repository;

import com.stayease.property.entity.PropertyAuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PropertyAuditEventRepository extends JpaRepository<PropertyAuditEvent, Long>, JpaSpecificationExecutor<PropertyAuditEvent> {
    List<PropertyAuditEvent> findByEntityIdOrderByTimestampDesc(String entityId);
}
