package org.ngelmakproject.web.rest.dto;

import java.time.LocalDate;

import org.ngelmakproject.domain.Subscription;

public record SubscriptionDTO(
		Long id,
		Long SubscriberId,
		Long SubscribedToId,
		LocalDate subscribedAt) {
	public static SubscriptionDTO from(Subscription s) {
		return new SubscriptionDTO(s.getId(), s.getSubscriber().getId(), s.getSubscribedTo().getId(),
				s.getSubscribedAt());
	}
}
