package com.stayease.review.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RemoveReviewRequest {

    @NotBlank(message = "{NotBlank.removeReviewRequest.reason}")
    @Size(max = 255, message = "{Size.removeReviewRequest.reason}")
    private String reason;
}
