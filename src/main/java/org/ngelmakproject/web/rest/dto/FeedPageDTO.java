package org.ngelmakproject.web.rest.dto;

import java.time.Instant;
import java.util.List;

/**
 * A DTO representing a paginated response.
 *
 * @param <T> the type of content in the page
 */
public record FeedPageDTO<T>(
		List<T> content,
		String sessionKey,
		Instant windowStart,
		Integer number,
		List<SortDTO> sorts) {
}