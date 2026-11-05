package com.stayease.property.repository;

import com.stayease.property.entity.RateOverride;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface RateOverrideRepository extends JpaRepository<RateOverride, Long> {

    List<RateOverride> findByRoomTypeId(Long roomTypeId);

    @Query("SELECT ro FROM RateOverride ro " +
           "WHERE ro.roomType.id = :roomTypeId " +
           "  AND ro.startDate <= :endDate " +
           "  AND ro.endDate >= :startDate")
    List<RateOverride> findOverlappingOverrides(
            @Param("roomTypeId") Long roomTypeId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );
}
