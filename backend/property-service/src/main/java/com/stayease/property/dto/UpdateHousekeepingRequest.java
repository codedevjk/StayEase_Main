package com.stayease.property.dto;

import com.stayease.property.enums.HousekeepingStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateHousekeepingRequest {

    @NotNull(message = "Housekeeping status is required")
    private HousekeepingStatus housekeepingStatus;
}
