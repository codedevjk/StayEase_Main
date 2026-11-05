package com.stayease.property.repository;

import com.stayease.property.entity.Hotel;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface HotelRepository extends JpaRepository<Hotel, Long>, JpaSpecificationExecutor<Hotel> {

    boolean existsByNameIgnoreCaseAndDestinationIgnoreCase(String name, String destination);

    @EntityGraph(attributePaths = {"amenities", "roomTypes"})
    @Query("SELECT DISTINCT h FROM Hotel h WHERE h.id = :id")
    Optional<Hotel> findDetailedById(@Param("id") Long id);
}
