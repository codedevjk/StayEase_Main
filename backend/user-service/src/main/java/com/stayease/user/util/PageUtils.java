package com.stayease.user.util;

import com.stayease.user.dto.PageResponse;
import org.springframework.data.domain.Page;

/**
 * Utility for mapping Spring Data {@link Page} to StayEase normalized {@link PageResponse}.
 */
public final class PageUtils {

    private PageUtils() {}

    public static <T> PageResponse<T> toPageResponse(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast(),
                page.isEmpty()
        );
    }
}
