package com.stayease.user.repository;

import com.stayease.user.entity.UserAuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link UserAuditEvent}.
 * Supports dynamic filtering using JPA Specifications.
 */
@Repository
public interface UserAuditEventRepository extends JpaRepository<UserAuditEvent, Long>, JpaSpecificationExecutor<UserAuditEvent> {
}
