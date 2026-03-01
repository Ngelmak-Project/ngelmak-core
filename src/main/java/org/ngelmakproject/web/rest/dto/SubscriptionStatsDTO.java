package org.ngelmakproject.web.rest.dto;

import java.util.List;

public record SubscriptionStatsDTO(
        Long channelId,
        int followersCount,
        int followingCount,
        List<SubscriptionDTO> followers,
        List<SubscriptionDTO> following) {
}
