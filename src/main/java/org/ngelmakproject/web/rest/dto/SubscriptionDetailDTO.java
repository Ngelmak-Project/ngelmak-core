package org.ngelmakproject.web.rest.dto;

import java.time.LocalDate;

import org.ngelmakproject.domain.Subscription;

public record SubscriptionDetailDTO(
		Long id,
		ChannelDTO subscriber,
		ChannelDTO subscribedTo,
		LocalDate subscribedAt) {

	public static SubscriptionDetailDTO from(Subscription s) {
		return new SubscriptionDetailDTO(
				s.getId(),
				ChannelDTO.from(s.getSubscriber()),
				ChannelDTO.from(s.getSubscribedTo()),
				s.getSubscribedAt());
	}
}
