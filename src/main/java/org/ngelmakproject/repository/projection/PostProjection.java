package org.ngelmakproject.repository.projection;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public record PostProjection(
		Long id,
		Instant at,
		Instant lastUpdate,
		Instant deletedAt,
		String content,
		Long replyToId,
		Long channelId,
		Set<Long> fileIds) implements Serializable {

	public static Optional<PostProjection> from(List<PostRow> rows) {
		if (rows == null || rows.isEmpty()) {
			return Optional.empty();
		}

		var first = rows.getFirst(); // safe in Java 21; use rows.get(0) otherwise

		Set<Long> fileIds = rows.stream()
				.map(PostRow::fileId)
				.filter(Objects::nonNull)
				.collect(Collectors.toSet());

		return Optional.of(new PostProjection(
				first.id(),
				first.at(),
				first.lastUpdate(),
				first.deletedAt(),
				first.content(),
				first.replyToId(),
				first.channelId(),
				fileIds));
	}

	public static List<PostProjection> fromRows(List<PostRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }

        // Group rows by post ID
        Map<Long, List<PostRow>> grouped = rows.stream()
                .collect(Collectors.groupingBy(PostRow::id));

        // Build one PostProjection per post
        return grouped.values().stream()
                .map(postRows -> {
                    var first = postRows.getFirst(); // Java 21; use get(0) otherwise

                    Set<Long> fileIds = postRows.stream()
                            .map(PostRow::fileId)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toSet());

                    return new PostProjection(
                            first.id(),
                            first.at(),
                            first.lastUpdate(),
                            first.deletedAt(),
                            first.content(),
                            first.replyToId(),
                            first.channelId(),
                            fileIds
                    );
                })
                .toList();
    }
}
