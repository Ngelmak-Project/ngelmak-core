package org.ngelmakproject.repository.projection;

import java.time.Instant;
import java.util.Set;

public interface PostProjection {
    Long getId();

    Instant getAt();

    Instant getLastUpdate();

    Instant getDeletedAt();

    String getContent();

    Long getReplyToId();

    Long getChannelId();

    Set<FileProjection> getFiles();

    interface FileProjection {
        Long getId();
    }
}
