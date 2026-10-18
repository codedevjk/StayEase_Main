package com.stayease.user.repository;

import com.stayease.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data JPA repository for {@link User}.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    @Query("SELECT u.tokenVersion FROM User u WHERE u.id = :id")
    Optional<Long> findTokenVersionById(@Param("id") Long id);

    @Query("""
            SELECT u FROM User u
            WHERE (:query IS NULL OR :query = '' OR
                   LOWER(u.fullName) LIKE LOWER(CONCAT('%', :query, '%')) OR
                   LOWER(u.email) LIKE LOWER(CONCAT('%', :query, '%')))
            """)
    Page<User> searchUsers(@Param("query") String query, Pageable pageable);
}
