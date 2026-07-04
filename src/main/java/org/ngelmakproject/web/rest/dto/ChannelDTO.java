package org.ngelmakproject.web.rest.dto;

import java.time.Instant;

import org.ngelmakproject.domain.Channel;
import org.ngelmakproject.repository.projection.ChannelProjection;

public record ChannelDTO(
        Long id,
        String identifier,
        String name,
        String description,
        String avatar,
        String banner,
        Instant createdAt,
        Long userId,
        EngagementStats stats) {
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

    public static ChannelDTO from(Channel a, EngagementStats stats) {
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

    public static ChannelDTO from(ChannelProjection a, EngagementStats stats) {
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
                null,
                stats);
    }
}
