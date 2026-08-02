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
    public static ChannelDTO from(Channel c) {
        if (c == null)
            return null;
        return new ChannelDTO(
                c.getId(),
                c.getIdentifier(),
                c.getName(),
                c.getDescription(),
                c.getAvatar(),
                c.getBanner(),
                c.getCreatedAt(),
                c.getUser(),
                EngagementStats.empty(c.getId()));
    }

    public static ChannelDTO from(Channel c, EngagementStats stats) {
        if (c == null)
            return null;
        return new ChannelDTO(
                c.getId(),
                c.getIdentifier(),
                c.getName(),
                c.getDescription(),
                c.getAvatar(),
                c.getBanner(),
                c.getCreatedAt(),
                c.getUser(),
                stats);
    }

    public static ChannelDTO from(ChannelProjection c, EngagementStats stats) {
        if (c == null)
            return null;
        return new ChannelDTO(
                c.id(),
                c.identifier(),
                c.name(),
                c.description(),
                c.avatar(),
                c.banner(),
                c.createdAt(),
                null,
                stats);
    }
}
