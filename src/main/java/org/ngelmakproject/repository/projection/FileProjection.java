package org.ngelmakproject.repository.projection;

import java.time.Instant;

public interface FileProjection {
    public Long getId();

    public String getHash();

    public String getFilename();

    public Long getSize();

    public Integer getDuration();

    public String getUrl();

    public String getInternalUrl();

    public String getType();

    public Integer getUsageCount();

    public Instant getDeletedAt();

    public FileRef getCover();

    interface FileRef {
        Long getId();
    }
}
