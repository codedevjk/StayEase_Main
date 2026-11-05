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
public class HotelDetailDto {
    private Long id;
    private String name;
    private String destination;
    private String address;
    private String contactEmail;
    private String contactPhone;
    private String description;
    private Integer starRating;
    private BigDecimal publishedRating;
    private Integer reviewCount;
    private String imageUrl;
    private Boolean active;
    private List<AmenityDto> amenities;
    private List<RoomTypeDetailDto> roomTypes;
}
