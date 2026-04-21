package org.ngelmakproject.repository.projection;

public interface ActiveChannelProjection {
    Long getId();

    String getName();

    String getIdentifier();

    String getBanner();

    String getAvatar();

    String getDescription();

    Integer getPostCount();
}
