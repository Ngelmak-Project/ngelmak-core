package org.ngelmakproject.web.rest.dto;

import java.time.LocalDate;

import org.ngelmakproject.domain.Subscription;

public record SubscriptionDTO(
		Long id,
		Long subscriberId,
		Long subscribedToId,
		LocalDate subscribedAt) {
	public static SubscriptionDTO from(Subscription s) {
		return new SubscriptionDTO(s.getId(), s.getSubscriber().getId(), s.getSubscribedTo().getId(),
				s.getSubscribedAt());
	}
}
