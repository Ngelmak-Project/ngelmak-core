package org.ngelmakproject.web.rest.dto;

import java.time.Instant;

import org.ngelmakproject.domain.Channel;

public record ChannelDTO(
        Long id,
        String identifier,
        String name,
        String description,
        String avatar,
        String banner,
        Instant createdAt,
        Long userId,
        SubscriptionStatsDTO stats) {
    public static ChannelDTO from(Channel a) {
        if (a == null)
            return null;
        return new ChannelDTO(
                a.getId(),
                a.getIdentifier(),
                a.getName(),
                a.getDescription(),
                a.getAvatar(),
                a.getBanner(),
                a.getCreatedAt(),
                a.getUser(), null);
    }

    public static ChannelDTO from(Channel a, SubscriptionStatsDTO stats) {
        if (a == null)
            return null;
        return new ChannelDTO(
                a.getId(),
                a.getIdentifier(),
                a.getName(),
                a.getDescription(),
                a.getAvatar(),
                a.getBanner(),
                a.getCreatedAt(),
                a.getUser(),
                stats);
    }
}
