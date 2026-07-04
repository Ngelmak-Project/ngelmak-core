package org.ngelmakproject.repository.projection;

import java.time.Instant;

public interface ChannelProjection {
    Long getId();

    String getName();

    String getIdentifier();

    String getBanner();

    String getAvatar();

    String getDescription();

    Instant getCreatedAt();

    Integer getPostCount();
}
