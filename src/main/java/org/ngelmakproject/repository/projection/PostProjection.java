package org.ngelmakproject.repository.projection;

import java.time.Instant;
import java.util.Set;

public interface PostProjection {
    Long getId();

    Instant getAt();

    Instant getLastUpdate();

    Instant getDeletedAt();

    String getContent();

    replyToRef getreplyTo();

    ChannelRef getChannel();

    Set<FileRef> getFiles();

    interface replyToRef {
        Long getId();
    }

    interface ChannelRef {
        Long getId();
    }

    interface FileRef {
        Long getId();
    }
}
