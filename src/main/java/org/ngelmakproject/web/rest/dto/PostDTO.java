package org.ngelmakproject.web.rest.dto;

import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;

import org.ngelmakproject.domain.Post;
import org.ngelmakproject.domain.Post.Status;

public record PostDTO(
        Long id,
        String content,
        Instant at,
        Instant lastUpdate,
        Boolean visibility,
        Status status,
        ChannelDTO channel,
        Set<FileDTO> files,
        ReactionSummaryDTO reactions,
        Integer commentCount,
        PostDTO postReply) {
    public static PostDTO from(Post p, ReactionSummaryDTO reactions) {
        if (p == null)
            return null;
        return new PostDTO(
                p.getId(),
                p.getContent(),
                p.getAt(),
                p.getLastUpdate(),
                p.getVisible(),
                p.getStatus(),
                ChannelDTO.from(p.getChannel()),
                p.getFiles().stream().map(FileDTO::from).collect(Collectors.toSet()),
                reactions,
                p.getCommentCount(),
                from(p.getPostReply(), null));
    }
}
