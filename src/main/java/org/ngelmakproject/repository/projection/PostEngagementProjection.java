package org.ngelmakproject.repository.projection;

import java.time.Instant;

/**
 * Projection interface for Post engagement metrics.
 * Combines the Post entity with aggregated engagement data such as reaction count and engagement score.
 *
 * This projection is used to efficiently retrieve posts along with their engagement metrics in a single query,
 * reducing the need for multiple database calls and improving performance when displaying trending or popular posts.
 */
public interface PostEngagementProjection {
    Long getId();
    Instant getAt();
    Long getCommentCount();
    Long getReactionCount();
}

