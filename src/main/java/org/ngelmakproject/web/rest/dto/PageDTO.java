package org.ngelmakproject.web.rest.dto;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Slice;

/**
 * A DTO representing a paginated response.
 *
 * @param <T> the type of content in the page
 */
public record PageDTO<T>(
        List<T> content,
        Integer number,
        Integer size,
        Long totalElements,
        Integer totalPages,
        Boolean isLast,
        Boolean isFirst,
        Boolean hasNext,
        Boolean hasPrevious,
        List<SortDTO> sorts) {

    public static <T> PageDTO<T> from(Page<T> page) {
        return new PageDTO<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast(),
                page.isFirst(),
                page.hasNext(),
                page.hasPrevious(),
                page.getSort().stream()
                        .map(order -> new SortDTO(order.getProperty(), order.getDirection().name()))
                        .toList());
    }

    public static <T> PageDTO<T> from(Slice<T> slice) {
        return new PageDTO<>(
                slice.getContent(),
                slice.getNumber(),
                slice.getSize(),
                null, // totalElements unknown for Slice
                null, // totalPages unknown for Slice
                null, // isLast unknown for Slice
                null, // isFirst unknown for Slice
                slice.hasNext(),
                null, // hasPrevious unknown for Slice
                slice.getSort().stream()
                        .map(order -> new SortDTO(order.getProperty(), order.getDirection().name()))
                        .toList());
    }
}
