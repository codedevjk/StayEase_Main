package com.stayease.property.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateHotelRequest {

    @NotBlank(message = "Hotel name is required")
    @Size(min = 2, max = 150, message = "Hotel name must be between 2 and 150 characters")
    private String name;

    @NotBlank(message = "Destination is required")
    @Size(min = 2, max = 100, message = "Destination must be between 2 and 100 characters")
    private String destination;

    @NotBlank(message = "Address is required")
    @Size(max = 255, message = "Address cannot exceed 255 characters")
    private String address;

    @NotBlank(message = "Contact email is required")
    @Email(message = "Invalid contact email address")
    @Size(max = 150, message = "Contact email cannot exceed 150 characters")
    private String contactEmail;

    @NotBlank(message = "Contact phone is required")
    @Pattern(regexp = "^[0-9]{10,15}$", message = "Phone number must be between 10 and 15 digits")
    private String contactPhone;

    @Size(max = 2000, message = "Description cannot exceed 2000 characters")
    private String description;

    @Min(value = 1, message = "Star rating must be at least 1")
    @Max(value = 5, message = "Star rating cannot exceed 5")
    private Integer starRating;

    @Size(max = 255, message = "Image URL cannot exceed 255 characters")
    private String imageUrl;

    private Boolean active;

    private Set<Long> amenityIds;
}
