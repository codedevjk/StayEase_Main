package com.stayease.reservation.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoomTypeInventoryId implements Serializable {

    private Long roomTypeId;
    private LocalDate stayDate;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RoomTypeInventoryId that = (RoomTypeInventoryId) o;
        return Objects.equals(roomTypeId, that.roomTypeId) && Objects.equals(stayDate, that.stayDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(roomTypeId, stayDate);
    }
}
