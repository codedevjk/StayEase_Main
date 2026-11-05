package com.stayease.property.service;

import com.stayease.property.dto.PageResponse;
import com.stayease.property.dto.*;
import com.stayease.property.entity.Amenity;
import com.stayease.property.entity.Hotel;
import com.stayease.property.entity.RoomType;
import com.stayease.property.exception.ResourceNotFoundException;
import com.stayease.property.repository.AmenityRepository;
import com.stayease.property.repository.HotelRepository;
import com.stayease.property.repository.PhysicalRoomRepository;
import jakarta.persistence.criteria.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service managing hotel catalog, multi-criteria discovery search, and hotel details.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HotelService {

    private final HotelRepository hotelRepository;
    private final AmenityRepository amenityRepository;
    private final PhysicalRoomRepository physicalRoomRepository;
    private final PropertyAuditService propertyAuditService;

    @Transactional(readOnly = true)
    public PageResponse<HotelSummaryDto> searchHotelsWithRawAmenities(
            String destination,
            String query,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            List<String> rawAmenities,
            BigDecimal minRating,
            Pageable pageable) {
        return searchHotelsWithRawAmenities(destination, query, minPrice, maxPrice, rawAmenities, minRating, null, pageable);
    }

    @Transactional(readOnly = true)
    public PageResponse<HotelSummaryDto> searchHotelsWithRawAmenities(
            String destination,
            String query,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            List<String> rawAmenities,
            BigDecimal minRating,
            Boolean active,
            Pageable pageable) {
        List<Long> amenityIds = resolveAmenityIds(rawAmenities);
        return searchHotels(destination, query, minPrice, maxPrice, amenityIds, minRating, active, pageable);
    }

    private List<Long> resolveAmenityIds(List<String> rawAmenities) {
        if (rawAmenities == null || rawAmenities.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> ids = new ArrayList<>();
        List<Amenity> allAmenities = null;
        for (String item : rawAmenities) {
            if (item == null || item.isBlank()) continue;
            String[] tokens = item.split(",");
            for (String token : tokens) {
                String clean = token.trim();
                if (clean.isEmpty()) continue;
                try {
                    ids.add(Long.parseLong(clean));
                } catch (NumberFormatException ignored) {
                    if (allAmenities == null) {
                        allAmenities = amenityRepository.findAll();
                    }
                    String normalizedClean = clean.replaceAll("[\\s_\\-]+", "").toLowerCase();
                    allAmenities.stream()
                            .filter(a -> a.getName() != null && a.getName().replaceAll("[\\s_\\-]+", "").equalsIgnoreCase(normalizedClean))
                            .map(Amenity::getId)
                            .findFirst()
                            .ifPresent(ids::add);
                }
            }
        }
        return ids;
    }

    @Transactional(readOnly = true)
    public PageResponse<HotelSummaryDto> searchHotels(
            String destination,
            String query,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            List<Long> amenityIds,
            BigDecimal minRating,
            Pageable pageable) {
        return searchHotels(destination, query, minPrice, maxPrice, amenityIds, minRating, null, pageable);
    }

    @Transactional(readOnly = true)
    public PageResponse<HotelSummaryDto> searchHotels(
            String destination,
            String query,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            List<Long> amenityIds,
            BigDecimal minRating,
            Boolean active,
            Pageable pageable) {

        Specification<Hotel> spec = (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 1. Destination filter
            if (destination != null && !destination.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("destination")), "%" + destination.trim().toLowerCase() + "%"));
            }

            // 2. Query search by hotel name
            if (query != null && !query.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("name")), "%" + query.trim().toLowerCase() + "%"));
            }

            // 3. Minimum rating filter: checks official starRating or publishedRating
            if (minRating != null) {
                Predicate starPred = cb.greaterThanOrEqualTo(root.get("starRating"), minRating.intValue());
                Predicate pubPred = cb.greaterThanOrEqualTo(root.get("publishedRating"), minRating);
                predicates.add(cb.or(starPred, pubPred));
            }

            // 4. Amenity filter (hotel must have ALL requested amenity IDs)
            if (amenityIds != null && !amenityIds.isEmpty()) {
                Subquery<Long> subquery = criteriaQuery.subquery(Long.class);
                Root<Hotel> subRoot = subquery.from(Hotel.class);
                Join<Hotel, Amenity> amenityJoin = subRoot.join("amenities");
                subquery.select(subRoot.get("id"))
                        .where(amenityJoin.get("id").in(amenityIds))
                        .groupBy(subRoot.get("id"))
                        .having(cb.equal(cb.count(amenityJoin.get("id")), (long) amenityIds.size()));

                predicates.add(root.get("id").in(subquery));
            }

            // 5. Price range filter across room types
            if (minPrice != null || maxPrice != null) {
                Subquery<Long> priceSubquery = criteriaQuery.subquery(Long.class);
                Root<RoomType> rtRoot = priceSubquery.from(RoomType.class);
                List<Predicate> rtPreds = new ArrayList<>();
                rtPreds.add(cb.equal(rtRoot.get("hotel").get("id"), root.get("id")));

                if (minPrice != null) {
                    rtPreds.add(cb.greaterThanOrEqualTo(rtRoot.get("baseNightlyRate"), minPrice));
                }
                if (maxPrice != null) {
                    rtPreds.add(cb.lessThanOrEqualTo(rtRoot.get("baseNightlyRate"), maxPrice));
                }

                priceSubquery.select(rtRoot.get("hotel").get("id")).where(rtPreds.toArray(new Predicate[0]));
                predicates.add(cb.exists(priceSubquery));
            }

            // 6. Active status filter
            if (active != null) {
                predicates.add(cb.equal(root.get("active"), active));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<Hotel> page = hotelRepository.findAll(spec, pageable);

        List<HotelSummaryDto> summaries = page.getContent().stream()
                .map(this::mapToSummaryDto)
                .collect(Collectors.toList());

        return new PageResponse<>(
                summaries,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast(),
                page.isEmpty()
        );
    }

    @Transactional(readOnly = true)
    public HotelDetailDto getHotelDetails(Long hotelId) {
        Hotel hotel = hotelRepository.findDetailedById(hotelId)
                .orElseThrow(() -> new ResourceNotFoundException("Hotel not found with ID: " + hotelId));

        List<AmenityDto> amenityDtos = hotel.getAmenities().stream()
                .map(a -> AmenityDto.builder()
                        .id(a.getId())
                        .name(a.getName())
                        .icon(a.getIcon())
                        .build())
                .sorted(Comparator.comparing(AmenityDto::getName))
                .collect(Collectors.toList());

        List<RoomTypeDetailDto> roomTypeDtos = hotel.getRoomTypes().stream()
                .collect(Collectors.toMap(
                        rt -> rt.getId(),
                        rt -> rt,
                        (existing, duplicate) -> existing,
                        LinkedHashMap::new
                ))
                .values().stream()
                .map(rt -> {
                    long totalRooms = physicalRoomRepository.countByRoomTypeId(rt.getId());
                    return RoomTypeDetailDto.builder()
                            .id(rt.getId())
                            .name(rt.getName())
                            .description(rt.getDescription())
                            .maxGuests(rt.getMaxGuests())
                            .baseNightlyRate(rt.getBaseNightlyRate())
                            .totalRooms((int) totalRooms)
                            .build();
                })
                .sorted(Comparator.comparing(RoomTypeDetailDto::getId))
                .collect(Collectors.toList());

        return HotelDetailDto.builder()
                .id(hotel.getId())
                .name(hotel.getName())
                .destination(hotel.getDestination())
                .address(hotel.getAddress())
                .contactEmail(hotel.getContactEmail())
                .contactPhone(hotel.getContactPhone())
                .description(hotel.getDescription())
                .starRating(hotel.getStarRating())
                .publishedRating(hotel.getPublishedRating())
                .reviewCount(hotel.getReviewCount())
                .imageUrl(hotel.getImageUrl())
                .active(hotel.getActive() != null ? hotel.getActive() : true)
                .amenities(amenityDtos)
                .roomTypes(roomTypeDtos)
                .build();
    }

    @Transactional
    public HotelDetailDto createHotel(CreateHotelRequest request) {
        Hotel hotel = Hotel.builder()
                .name(request.getName().trim())
                .destination(request.getDestination().trim())
                .address(request.getAddress().trim())
                .contactEmail(request.getContactEmail().trim().toLowerCase())
                .contactPhone(request.getContactPhone().trim())
                .description(request.getDescription())
                .starRating(request.getStarRating() != null ? request.getStarRating() : 3)
                .publishedRating(BigDecimal.valueOf(0.00).setScale(2, RoundingMode.HALF_UP))
                .reviewCount(0)
                .imageUrl(request.getImageUrl())
                .active(request.getActive() != null ? request.getActive() : true)
                .build();

        if (request.getAmenityIds() != null && !request.getAmenityIds().isEmpty()) {
            List<Amenity> amenities = amenityRepository.findAllById(request.getAmenityIds());
            hotel.setAmenities(new HashSet<>(amenities));
        }

        Hotel saved = hotelRepository.save(hotel);

        propertyAuditService.recordAudit(
                0L,
                "ADMIN",
                "HOTEL_OPERATION",
                "HOTEL_CREATED",
                String.valueOf(saved.getId()),
                "Created hotel: " + saved.getName() + " in " + saved.getDestination(),
                null,
                "{\"name\":\"" + saved.getName() + "\",\"destination\":\"" + saved.getDestination() + "\"}"
        );

        log.info("Created hotel '{}' (ID {}) in {}", saved.getName(), saved.getId(), saved.getDestination());
        return getHotelDetails(saved.getId());
    }

    @Transactional
    public HotelDetailDto updateHotel(Long hotelId, UpdateHotelRequest request) {
        Hotel hotel = hotelRepository.findById(hotelId)
                .orElseThrow(() -> new ResourceNotFoundException("Hotel not found with ID: " + hotelId));

        hotel.setName(request.getName().trim());
        hotel.setDestination(request.getDestination().trim());
        hotel.setAddress(request.getAddress().trim());
        hotel.setContactEmail(request.getContactEmail().trim().toLowerCase());
        hotel.setContactPhone(request.getContactPhone().trim());
        hotel.setDescription(request.getDescription());
        if (request.getStarRating() != null) {
            hotel.setStarRating(request.getStarRating());
        }
        if (request.getImageUrl() != null) {
            hotel.setImageUrl(request.getImageUrl());
        }
        if (request.getActive() != null) {
            hotel.setActive(request.getActive());
        }

        if (request.getAmenityIds() != null) {
            List<Amenity> amenities = amenityRepository.findAllById(request.getAmenityIds());
            hotel.setAmenities(new HashSet<>(amenities));
        }

        Hotel updated = hotelRepository.save(hotel);
        log.info("Updated hotel ID {}", updated.getId());
        return getHotelDetails(updated.getId());
    }

    @Transactional
    public HotelDetailDto toggleHotelStatus(Long hotelId, boolean active) {
        Hotel hotel = hotelRepository.findById(hotelId)
                .orElseThrow(() -> new ResourceNotFoundException("Hotel not found with ID: " + hotelId));

        hotel.setActive(active);
        Hotel updated = hotelRepository.save(hotel);

        propertyAuditService.recordAudit(
                0L,
                "ADMIN",
                "HOTEL_OPERATION",
                "HOTEL_STATUS_TOGGLED",
                String.valueOf(updated.getId()),
                "Hotel " + updated.getName() + " active status set to " + active,
                null,
                "{\"active\":" + active + "}"
        );

        log.info("Toggled hotel ID {} active status to {}", hotelId, active);
        return getHotelDetails(updated.getId());
    }

    private HotelSummaryDto mapToSummaryDto(Hotel hotel) {
        BigDecimal startingRate = hotel.getRoomTypes().stream()
                .map(RoomType::getBaseNightlyRate)
                .min(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);

        List<String> amenityNames = hotel.getAmenities().stream()
                .map(Amenity::getName)
                .sorted()
                .collect(Collectors.toList());

        return HotelSummaryDto.builder()
                .id(hotel.getId())
                .name(hotel.getName())
                .destination(hotel.getDestination())
                .address(hotel.getAddress())
                .starRating(hotel.getStarRating())
                .publishedRating(hotel.getPublishedRating())
                .reviewCount(hotel.getReviewCount())
                .startingNightlyRate(startingRate.setScale(2, RoundingMode.HALF_UP))
                .imageUrl(hotel.getImageUrl())
                .active(hotel.getActive() != null ? hotel.getActive() : true)
                .amenities(amenityNames)
                .build();
    }
}
