package com.stayease.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Standardized pagination envelope for all list endpoints.
 *
 * <p>All paged REST endpoints in StayEase must wrap their results in this
 * generic record. Callers receive consistent pagination metadata regardless
 * of the resource type.
 *
 * <p>Query parameters accepted by all paged endpoints:
 * <ul>
 *   <li>{@code page} — 0-indexed page number (default: 0)</li>
 *   <li>{@code size} — items per page (default: 20, max: 100)</li>
 * </ul>
 *
 * <p>Example JSON:
 * <pre>{@code
 * {
 *   "content": [...],
 *   "pageNumber": 0,
 *   "pageSize": 20,
 *   "totalElements": 150,
 *   "totalPages": 8,
 *   "first": true,
 *   "last": false,
 *   "empty": false
 * }
 * }</pre>
 *
 * @param <T> the type of the content items
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PageResponse<T>(

        /** The items on the current page. */
        List<T> content,

        /** 0-indexed page number of the current page. */
        int pageNumber,

        /** Maximum number of items per page. */
        int pageSize,

        /** Total number of items across all pages. */
        long totalElements,

        /** Total number of pages given the current page size. */
        int totalPages,

        /** True if this is the first page. */
        boolean first,

        /** True if this is the last page. */
        boolean last,

        /** True if the content list is empty. */
        boolean empty
) {

    /**
     * Factory method to construct a {@link PageResponse} from a Spring Data
     * {@code org.springframework.data.domain.Page}.
     * Services should use this in their mapper/assembly layer.
     *
     * <p>Implementation is not included here to keep stayease-common free of
     * Spring Data dependencies. Each service module provides its own static
     * factory or uses a shared mapper utility within the service layer.
     */
}
