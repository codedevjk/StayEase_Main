package com.stayease.property.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HotelSummaryDto {
    private Long id;
    private String name;
    private String destination;
    private String address;
    private Integer starRating;
    private BigDecimal publishedRating;
    private Integer reviewCount;
    private BigDecimal startingNightlyRate;
    private String imageUrl;
    private Boolean active;
    private List<String> amenities;
}
