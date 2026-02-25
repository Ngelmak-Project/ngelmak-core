package org.ngelmakproject.web.rest.dto;

import java.time.Instant;

import org.ngelmakproject.domain.Review;
import org.ngelmakproject.domain.Review.Visibility;

public record ReviewDTO(
		Long id,
		Instant createdAt,
		String content,
		Visibility visibility,
		TicketDTO ticket,
		ReviewDTO replyTo,
		boolean isAuthor) {

	public static ReviewDTO from(Review r) {
		if (r == null)
			return null;
		return new ReviewDTO(
				r.getId(),
				r.getCreatedAt(),
				r.getContent(),
				r.getVisibility(),
				TicketDTO.from(r.getTicket()),
				ReviewDTO.from(r.getReplyTo(), false),
				false);
	}

	public static ReviewDTO from(Review r, boolean isAuthor) {
		if (r == null)
			return null;
		return new ReviewDTO(
				r.getId(),
				r.getCreatedAt(),
				r.getContent(),
				r.getVisibility(),
				TicketDTO.from(r.getTicket()),
				ReviewDTO.from(r.getReplyTo(), false),
				isAuthor);
	}
}
