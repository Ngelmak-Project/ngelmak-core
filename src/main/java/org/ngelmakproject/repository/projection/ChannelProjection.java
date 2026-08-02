package org.ngelmakproject.repository.projection;

import java.io.Serializable;
import java.time.Instant;

public record ChannelProjection(
		Long id,
		String name,
		String identifier,
		String avatar,
		String banner,
		String description,
		Instant createdAt,
		Long postCount) implements Serializable {
}
