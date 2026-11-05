package com.stayease.property.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssignCleanRoomResponse {
    private Long physicalRoomId;
    private String roomNumber;
}
