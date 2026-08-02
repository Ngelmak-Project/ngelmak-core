package org.ngelmakproject.repository.projection;

import java.io.Serializable;
import java.time.Instant;

public record CommentProjection(
        Long id,
        Instant at,
        String content,
        Integer replyCount,
        Instant lastUpdate,
        Instant deletedAt,
        Long postId,
        Long replyToId,
        Long channelId,
        Long fileId
    ) implements Serializable {
}
