package com.stayease.reservation.repository;

import com.stayease.reservation.entity.RoomAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RoomAssignmentRepository extends JpaRepository<RoomAssignment, Long> {

    Optional<RoomAssignment> findByReservationId(Long reservationId);

    Optional<RoomAssignment> findByPhysicalRoomId(Long physicalRoomId);
}
