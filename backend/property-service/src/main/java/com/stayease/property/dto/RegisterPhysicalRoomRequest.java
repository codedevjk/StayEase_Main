package com.stayease.property.dto;

import com.stayease.property.enums.HousekeepingStatus;
import com.stayease.property.enums.OperationalStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegisterPhysicalRoomRequest {

    @NotNull(message = "Room type ID is required")
    private Long roomTypeId;

    @NotBlank(message = "Room number is required")
    @Size(min = 1, max = 20, message = "Room number must be between 1 and 20 characters")
    private String roomNumber;

    @Builder.Default
    private HousekeepingStatus housekeepingStatus = HousekeepingStatus.CLEAN;

    @Builder.Default
    private OperationalStatus operationalStatus = OperationalStatus.OPERATIONAL;
}
