package org.ngelmakproject.repository.projection;

import java.time.Instant;

public interface CommentProjection {
    Long getId();

    Instant getAt();

    Instant getLastUpdate();

    Instant getDeletedAt();

    String getContent();

    PostRef getPost();

    CommentRef getReplyTo();

    ChannelRef getChannel();

    FileRef getFile();

    Integer getReplyCount();

    interface PostRef {
        Long getId();
    }

    interface CommentRef {
        Long getId();
    }

    interface ChannelRef {
        Long getId();
    }

    interface FileRef {
        Long getId();
    }
}
