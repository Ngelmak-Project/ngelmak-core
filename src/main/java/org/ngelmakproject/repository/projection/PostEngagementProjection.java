package org.ngelmakproject.repository.projection;

import org.ngelmakproject.domain.Post;

/**
 * Projection interface for Post engagement metrics.
 * Combines the Post entity with aggregated engagement data such as reaction count and engagement score.
 *
 * This projection is used to efficiently retrieve posts along with their engagement metrics in a single query,
 * reducing the need for multiple database calls and improving performance when displaying trending or popular posts.
 */
public record PostEngagementProjection(
    Post post,
    long reactionCount,
    double engagementScore
) {}

