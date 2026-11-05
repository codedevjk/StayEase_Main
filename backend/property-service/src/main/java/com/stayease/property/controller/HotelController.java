package com.stayease.property.controller;

import com.stayease.property.dto.PageResponse;
import com.stayease.property.dto.HotelDetailDto;
import com.stayease.property.dto.HotelSummaryDto;
import com.stayease.property.service.HotelService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/hotels")
@RequiredArgsConstructor
public class HotelController {

    private final HotelService hotelService;

    @GetMapping
    public ResponseEntity<PageResponse<HotelSummaryDto>> searchHotels(
            @RequestParam(required = false) String destination,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) List<String> amenities,
            @RequestParam(required = false) BigDecimal minRating,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir
    ) {
        int boundedPage = Math.max(0, page);
        int boundedSize = Math.max(1, Math.min(size, 100));

        Sort sort = sortDir.equalsIgnoreCase("desc")
                ? Sort.by(sortBy).descending()
                : Sort.by(sortBy).ascending();

        Pageable pageable = PageRequest.of(boundedPage, boundedSize, sort);

        PageResponse<HotelSummaryDto> response = hotelService.searchHotelsWithRawAmenities(
                destination, query, minPrice, maxPrice, amenities, minRating, active, pageable
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{hotelId}")
    public ResponseEntity<HotelDetailDto> getHotelDetails(@PathVariable Long hotelId) {
        HotelDetailDto response = hotelService.getHotelDetails(hotelId);
        return ResponseEntity.ok(response);
    }
}
