package com.stayease.review.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateReviewRequest {

    @NotNull(message = "{NotNull.createReviewRequest.reservationId}")
    private Long reservationId;

    @NotNull(message = "{NotNull.createReviewRequest.rating}")
    @Min(value = 1, message = "{Min.createReviewRequest.rating}")
    @Max(value = 5, message = "{Max.createReviewRequest.rating}")
    private Integer rating;

    @NotBlank(message = "{NotBlank.createReviewRequest.comment}")
    @Size(min = 10, max = 1000, message = "{Size.createReviewRequest.comment}")
    private String comment;
}
