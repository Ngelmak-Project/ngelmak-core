package org.ngelmakproject.web.rest.dto;

import java.util.List;

/**
 * A DTO representing a paginated response.
 *
 * @param <T> the type of content in the page
 */
public record FeedPageDTO<T>(
		List<T> content,
		String sessionKey,
		Integer number,
		List<SortDTO> sorts) {
}