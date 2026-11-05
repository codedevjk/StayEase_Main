package com.stayease.reservation.client;

import com.stayease.reservation.exception.ReservationConflictException;
import com.stayease.reservation.exception.ResourceNotFoundException;
import com.stayease.reservation.exception.ServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.time.LocalDate;

@Slf4j
@Component
public class PropertyServiceClient {

    private final WebClient webClient;

    @Autowired
    public PropertyServiceClient(
            WebClient.Builder webClientBuilder,
            @Value("${stayease.internal.property-service-url:http://stayease-property-service}") String propertyServiceUrl) {
        this.webClient = webClientBuilder.baseUrl(propertyServiceUrl).build();
    }

    @CircuitBreaker(name = "property-service", fallbackMethod = "getCapacityFallback")
    public PropertyCapacityDto getSellableCapacity(Long roomTypeId) {
        try {
            return webClient.get()
                    .uri("/internal/properties/room-types/{roomTypeId}/capacity", roomTypeId)
                    .retrieve()
                    .bodyToMono(PropertyCapacityDto.class)
                    .timeout(Duration.ofSeconds(3))
                    .block();
        } catch (WebClientResponseException.NotFound ex) {
            throw new ResourceNotFoundException("Room type with ID " + roomTypeId + " not found");
        } catch (Exception ex) {
            log.error("Error retrieving capacity for roomType {}: {}", roomTypeId, ex.getMessage());
            throw new ServiceUnavailableException("Property Service is currently unavailable");
        }
    }

    public PropertyCapacityDto getCapacityFallback(Long roomTypeId, Throwable t) {
        if (t instanceof ResourceNotFoundException) {
            throw (ResourceNotFoundException) t;
        }
        log.error("Fallback triggered for roomType {} capacity: {}", roomTypeId, t.getMessage());
        throw new ServiceUnavailableException("Property Service is currently unavailable");
    }

    @CircuitBreaker(name = "property-service", fallbackMethod = "getPricingFallback")
    public PropertyPricingDto getPricing(Long roomTypeId, LocalDate checkIn, LocalDate checkOut) {
        try {
            return webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/internal/properties/room-types/{roomTypeId}/pricing")
                            .queryParam("checkIn", checkIn)
                            .queryParam("checkOut", checkOut)
                            .build(roomTypeId))
                    .retrieve()
                    .bodyToMono(PropertyPricingDto.class)
                    .timeout(Duration.ofSeconds(3))
                    .block();
        } catch (WebClientResponseException.NotFound ex) {
            throw new ResourceNotFoundException("Room type with ID " + roomTypeId + " not found");
        } catch (Exception ex) {
            log.error("Error retrieving pricing for roomType {}: {}", roomTypeId, ex.getMessage());
            throw new ServiceUnavailableException("Property Service is currently unavailable");
        }
    }

    public PropertyPricingDto getPricingFallback(Long roomTypeId, LocalDate checkIn, LocalDate checkOut, Throwable t) {
        if (t instanceof ResourceNotFoundException) {
            throw (ResourceNotFoundException) t;
        }
        log.error("Fallback triggered for roomType {} pricing: {}", roomTypeId, t.getMessage());
        throw new ServiceUnavailableException("Property Service is currently unavailable");
    }

    @CircuitBreaker(name = "property-service", fallbackMethod = "getHotelDetailsFallback")
    public PropertyHotelDto getHotelDetails(Long hotelId) {
        try {
            return webClient.get()
                    .uri("/api/hotels/{hotelId}", hotelId)
                    .retrieve()
                    .bodyToMono(PropertyHotelDto.class)
                    .timeout(Duration.ofSeconds(3))
                    .block();
        } catch (WebClientResponseException.NotFound ex) {
            throw new ResourceNotFoundException("Hotel with ID " + hotelId + " not found");
        } catch (Exception ex) {
            log.error("Error retrieving details for hotel {}: {}", hotelId, ex.getMessage());
            throw new ServiceUnavailableException("Property Service is currently unavailable");
        }
    }

    public PropertyHotelDto getHotelDetailsFallback(Long hotelId, Throwable t) {
        if (t instanceof ResourceNotFoundException) {
            throw (ResourceNotFoundException) t;
        }
        log.error("Fallback triggered for hotel {} details: {}", hotelId, t.getMessage());
        throw new ServiceUnavailableException("Property Service is currently unavailable");
    }

    @CircuitBreaker(name = "property-service", fallbackMethod = "assignCleanRoomFallback")
    public AssignCleanRoomResponse assignCleanRoom(AssignCleanRoomRequest request) {
        try {
            return webClient.post()
                    .uri("/internal/properties/rooms/assign-clean")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(AssignCleanRoomResponse.class)
                    .timeout(Duration.ofSeconds(3))
                    .block();
        } catch (WebClientResponseException ex) {
            if (ex.getStatusCode() == HttpStatus.CONFLICT) {
                throw new ReservationConflictException("No clean room currently ready. Front desk has been notified.");
            }
            if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new ResourceNotFoundException("Hotel or room type not found for check-in");
            }
            log.error("Error assigning clean room for reservation {}: {}", request.getReservationId(), ex.getMessage());
            throw new ServiceUnavailableException("Property Service is currently unavailable");
        } catch (Exception ex) {
            log.error("Error assigning clean room for reservation {}: {}", request.getReservationId(), ex.getMessage());
            throw new ServiceUnavailableException("Property Service is currently unavailable");
        }
    }

    public AssignCleanRoomResponse assignCleanRoomFallback(AssignCleanRoomRequest request, Throwable t) {
        if (t instanceof ReservationConflictException) {
            throw (ReservationConflictException) t;
        }
        if (t instanceof ResourceNotFoundException) {
            throw (ResourceNotFoundException) t;
        }
        log.error("Fallback triggered for assignCleanRoom: {}", t.getMessage());
        throw new ServiceUnavailableException("Property Service is currently unavailable");
    }

    public PhysicalRoomDto markRoomDirty(Long physicalRoomId) {
        try {
            return webClient.post()
                    .uri("/internal/properties/rooms/{roomId}/mark-dirty", physicalRoomId)
                    .retrieve()
                    .bodyToMono(PhysicalRoomDto.class)
                    .timeout(Duration.ofSeconds(3))
                    .block();
        } catch (Exception ex) {
            log.warn("Non-fatal: could not mark room {} as DIRTY immediately: {}", physicalRoomId, ex.getMessage());
            return null;
        }
    }
}
