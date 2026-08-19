package org.ngelmakproject.web.rest.dto;

import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;

import org.ngelmakproject.domain.Post;

public record PostDTO(
        Long id,
        String content,
        Instant at,
        Instant lastUpdate,
        Boolean visible,
        ChannelDTO channel,
        Set<FileDTO> files,
        ReactionSummaryDTO reactions,
        Integer commentCount,
        PostDTO replyTo) {
    public static PostDTO from(Post p, ReactionSummaryDTO reactions) {
        if (p == null)
            return null;
        return new PostDTO(
                p.getId(),
                p.getContent(),
                p.getAt(),
                p.getLastUpdate(),
                p.getVisible(),
                ChannelDTO.from(p.getChannel()),
                p.getFiles().stream().map(FileDTO::from).collect(Collectors.toSet()),
                reactions,
                p.getCommentCount(),
                from(p.getreplyTo(), null));
    }
}
