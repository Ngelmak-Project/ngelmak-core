package org.ngelmakproject.web.rest.dto;

import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;

import org.ngelmakproject.domain.Post;
import org.ngelmakproject.domain.enumeration.Status;
import org.ngelmakproject.domain.enumeration.Visibility;

public record PostDTO(
        Long id,
        String content,
        Instant at,
        Instant lastUpdate,
        Visibility visibility,
        Status status,
        ChannelDTO channel,
        Set<FileDTO> files,
        ReactionSummaryDTO reactions,
        int commentCount,
        Long replyToId) {
    public static PostDTO from(Post p, ReactionSummaryDTO reactions) {
        if (p == null)
            return null;
        return new PostDTO(
                p.getId(),
                p.getContent(),
                p.getAt(),
                p.getLastUpdate(),
                p.getVisibility(),
                p.getStatus(),
                ChannelDTO.from(p.getChannel()),
                p.getFiles().stream().map(FileDTO::from).collect(Collectors.toSet()),
                reactions,
                p.getCommentCount(),
                p.getPostReply() != null ? p.getPostReply().getId() : null);
    }
}
