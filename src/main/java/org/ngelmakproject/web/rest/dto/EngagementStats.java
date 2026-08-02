package org.ngelmakproject.web.rest.dto;

import java.util.List;

public record EngagementStats(
		Long channelId,
		Long postCount,
		Integer followersCount,
		Integer followingCount,
		List<SubscriptionDTO> followers,
		List<SubscriptionDTO> following) {
	public static EngagementStats empty(Long channelId) {
		return new EngagementStats(channelId, 0L, 0, 0, List.of(), List.of());
	}
}
