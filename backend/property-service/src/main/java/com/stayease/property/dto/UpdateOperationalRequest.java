package com.stayease.property.dto;

import com.stayease.property.enums.OperationalStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateOperationalRequest {

    @NotNull(message = "Operational status is required")
    private OperationalStatus operationalStatus;
}
