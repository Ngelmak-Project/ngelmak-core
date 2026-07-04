package org.ngelmakproject.web.rest.dto;

import java.util.List;

public record EngagementStats(
        Long channelId,
        Integer postCount,
        Integer followersCount,
        Integer followingCount,
        List<SubscriptionDTO> followers,
        List<SubscriptionDTO> following) {
}
