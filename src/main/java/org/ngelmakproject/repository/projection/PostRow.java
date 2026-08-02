package org.ngelmakproject.repository.projection;

import java.io.Serializable;
import java.time.Instant;

public record PostRow(
		Long id,
		Instant at,
		String content,
		Instant lastUpdate,
		Instant deletedAt,
		Long replyToId,
		Long channelId,
		Long fileId) implements Serializable {
}
