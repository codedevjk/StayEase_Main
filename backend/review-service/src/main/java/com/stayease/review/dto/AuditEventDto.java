package com.stayease.review.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditEventDto {
    private Long id;
    private String sourceService;
    private Long actorId;
    private String actorRole;
    private String eventCategory;
    private String action;
    private String entityId;
    private String description;
    private String beforeJson;
    private String afterJson;
    private Instant timestamp;
}
