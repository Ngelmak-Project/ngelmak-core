package org.ngelmakproject.web.rest.dto;

import org.ngelmakproject.domain.Channel;

public record ChannelDTO(
        Long id,
        String identifier,
        String name,
        String description,
        String avatar,
        String banner,
        Long userId) {
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
                a.getUser());
    }
}
