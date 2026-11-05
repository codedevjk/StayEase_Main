package com.stayease.review.repository;

import com.stayease.review.entity.Review;
import com.stayease.review.enums.ReviewStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {

    Optional<Review> findByReservationId(Long reservationId);

    boolean existsByReservationId(Long reservationId);

    Page<Review> findByHotelIdAndStatus(Long hotelId, ReviewStatus status, Pageable pageable);

    Page<Review> findByUserId(Long userId, Pageable pageable);

    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.hotelId = :hotelId AND r.status = :status")
    Double calculateAverageRating(@Param("hotelId") Long hotelId, @Param("status") ReviewStatus status);

    @Query("SELECT COUNT(r) FROM Review r WHERE r.hotelId = :hotelId AND r.status = :status")
    Long countByHotelIdAndStatus(@Param("hotelId") Long hotelId, @Param("status") ReviewStatus status);
}
