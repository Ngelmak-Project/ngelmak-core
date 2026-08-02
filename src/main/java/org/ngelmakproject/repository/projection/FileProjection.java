package org.ngelmakproject.repository.projection;

import java.io.Serializable;
import java.time.Instant;

public record FileProjection(
        Long id,
        String hash,
        String filename,
        Long size,
        Integer duration,
        String url,
        String internalUrl,
        String type,
        Integer usageCount,
        Instant createdAt,
        Long coverId) implements Serializable {
}
