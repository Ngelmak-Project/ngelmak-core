package org.ngelmakproject.repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.ngelmakproject.domain.Channel;
import org.ngelmakproject.domain.Post;
import org.ngelmakproject.repository.projection.CommentProjection;
import org.ngelmakproject.repository.projection.PostEngagementProjection;
import org.ngelmakproject.repository.projection.PostProjection;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.PersistenceException;

/**
 * Spring Data JPA repository for the Post entity.
 */
@SuppressWarnings("unused")
@Repository
public interface PostRepository extends JpaRepository<Post, Long> {
	Optional<PostProjection> findProjectedById(Long id);

	@Modifying
	@Query("UPDATE Post p SET p.deletedAt = :ts WHERE p.id IN :ids")
	int softDeleteByIds(List<Long> ids, @Param("ts") Instant ts);

	@Query("SELECT p FROM Post p WHERE p.deletedAt < :cutoff")
	List<PostProjection> findExpiredPosts(Instant cutoff);

	/**
	 * Fetches engagement metrics (comment count + reaction count) for all posts
	 * created after the given timestamp.
	 *
	 * <p>
	 * Used for periodic recency‑based score refreshes.
	 *
	 * @param since only posts with p.at >= since are included
	 * @return list of projections containing:
	 *         - post ID
	 *         - creation timestamp
	 *         - comment count
	 *         - reaction count (aggregated via LEFT JOIN)
	 */
	@Query("""
			SELECT p.id AS id,
			       p.at AS at,
			       p.commentCount AS commentCount,
			       COUNT(r) AS reactionCount
			FROM Post p
			LEFT JOIN Reaction r ON r.post.id = p.id
			WHERE p.at >= :since AND p.visible = true AND p.deletedAt IS NULL
			GROUP BY p.id
			""")
	List<PostEngagementProjection> fetchRecentEngagementMetricsByAtAfter(Instant since);

	/**
	 * Fetches engagement metrics (comment count + reaction count) for a specific
	 * set of post IDs.
	 *
	 * <p>
	 * Used when recomputing scores only for posts that changed (dirty posts).
	 *
	 * @param postIds list of post IDs to fetch metrics for
	 * @return list of projections containing:
	 *         - post ID
	 *         - creation timestamp
	 *         - comment count
	 *         - reaction count (aggregated via LEFT JOIN)
	 */
	@Query("""
			SELECT p.id AS id,
			       p.at AS at,
			       p.commentCount AS commentCount,
			       COUNT(r) AS reactionCount
			FROM Post p
			LEFT JOIN Reaction r ON r.post.id = p.id
			WHERE p.id IN :postIds AND p.visible = true AND p.deletedAt IS NULL
			GROUP BY p.id
			""")
	List<PostEngagementProjection> fetchEngagementMetricsByPostIds(List<Long> postIds);

	@Query(value = """
			SELECT DISTINCT p.*,
			(
			    (
			        (EXTRACT(EPOCH FROM p.at) - EXTRACT(EPOCH FROM CAST(:windowStart AS TIMESTAMP))) / 3600.0
			    )
			    * EXP(
			        -(
			            (EXTRACT(EPOCH FROM NOW()) - EXTRACT(EPOCH FROM p.at))
			            / 86400.0
			        )
			    )
			    * 2.0
			)
			+
			(
			    LEAST(p.comment_count, 20) * 0.5
			)
			+
			(
			    ((hashtext(CONCAT(:sessionKey, '-', p.id)) % 1000) / 1000.0 * 100.0)
			)
			AS score
			FROM post p
			LEFT JOIN channel c ON c.id = p.channel_id
			LEFT JOIN post r ON r.post_reply_id = p.id
			LEFT JOIN post_file pf ON pf.post_id = p.id
			LEFT JOIN file f ON f.id = pf.file_id
			WHERE p.at >= CAST(:windowStart AS TIMESTAMP)
			ORDER BY score DESC
			LIMIT :limit OFFSET :offset
			""", nativeQuery = true)
	List<Post> fetchFeedWithRelations(
			@Param("sessionKey") String sessionKey,
			@Param("windowStart") Instant windowStart,
			@Param("limit") int limit,
			@Param("offset") int offset);

	/**
	 * Fetches a list of post IDs for the user feed using a scoring algorithm that
	 * combines recency, exponential decay, comment count, and session-based
	 * randomization. Uses a CTE to precompute timestamps.
	 *
	 * @param sessionKey  A session-specific key used to introduce deterministic
	 *                    randomness.
	 * @param windowStart The earliest timestamp from which posts should be
	 *                    included.
	 * @param limit       Maximum number of post IDs to return.
	 * @param offset      Number of results to skip for pagination.
	 * @return A list of post IDs ordered by the computed feed score.
	 */
	@Query(value = """
			WITH params AS (
			    SELECT
			        EXTRACT(EPOCH FROM CURRENT_TIMESTAMP) AS now_epoch,
			        EXTRACT(EPOCH FROM :windowStart) AS window_epoch
			)
			SELECT p.id
			FROM post p
			CROSS JOIN params
			WHERE p.at >= :windowStart
			ORDER BY (
			        (
			            (EXTRACT(EPOCH FROM p.at) - params.window_epoch) / 3600.0
			        )
			        * EXP(-((params.now_epoch - EXTRACT(EPOCH FROM p.at)) / 86400.0))
			        * 2.0
			    )
			    + (LEAST(p.comment_count, 20) * 0.5)
			    + (((hashtext(:sessionKey || '-' || p.id) % 1000) / 1000.0) * 100.0)
			    DESC
			LIMIT :limit OFFSET :offset
			""", nativeQuery = true)
	List<Long> fetchFeedPostIdsOld(
			@Param("sessionKey") String sessionKey,
			@Param("windowStart") Instant windowStart,
			@Param("limit") int limit,
			@Param("offset") int offset);

	/**
	 * Fetches a list of post IDs for the user feed using epoch-based window start.
	 * Applies a scoring algorithm combining recency, exponential decay, comment
	 * count, and session-based randomization.
	 *
	 * @param sessionKey  A session-specific key used to introduce
	 *                    deterministic randomness.
	 * @param windowStart Representing the earliest allowed timestamp.
	 * @param limit       Maximum number of post IDs to return.
	 * @param offset      Number of results to skip for pagination.
	 * @return A list of post IDs ordered by the computed feed score.
	 */
	@Query(value = """
			SELECT p.id
			FROM post p
			WHERE p.at >= :windowStart
			ORDER BY (
			        EXTRACT(EPOCH FROM p.at - :windowStart) / 3600.0
			        * EXP(-(EXTRACT(EPOCH FROM NOW() - p.at) / 86400.0))
			        * 2.0
			    )
			    + (LEAST(p.comment_count, 20) * 0.5)
			    + (((hashtext(:sessionKey || '-' || p.id) % 1000) / 1000.0) * 100.0)
			    DESC
			LIMIT :limit OFFSET :offset
			""", nativeQuery = true)
	List<Long> fetchFeedPostIds(
			@Param("sessionKey") String sessionKey,
			@Param("windowStart") Instant windowStart,
			@Param("limit") int limit,
			@Param("offset") int offset);

	@Query(value = """
			SELECT p.id
			FROM post p
			WHERE p.at >= :windowStart
			ORDER BY
			    -- RECENCY (50%): Exponential decay, 48-hour half-life (older posts fade to ~6%)
			    EXP(-(EXTRACT(EPOCH FROM :nowValue - p.at) / 3600.0) / 48.0) * 0.50

			    -- ENGAGEMENT (30%): Logarithmic scale (100 comments ≈ 2.3x better than 10)
			    + (LN(1.0 + LEAST(p.comment_count, 100)) / LN(101.0)) * 0.30

			    -- SESSION RANDOMNESS (20%): Deterministic per (session, post) pair
			    -- Maps to 0.0-1.0 range, gives ±20% variation
			    + (((ABS(HASHTEXT(:sessionId || '-' || p.id)) % 100) / 100.0) * 0.20)
			DESC
			LIMIT :limit OFFSET :offset
			""", nativeQuery = true)
	List<Long> fetchFeedPostIds(
			@Param("sessionId") String sessionId, // Changed from sessionRandomValue
			@Param("windowStart") Instant windowStart,
			@Param("nowValue") Instant nowValue,
			@Param("limit") int limit,
			@Param("offset") int offset);

	/**
	 * Optimized version of the feed post ID fetcher.
	 * Precomputes NOW() and window start epochs using a CTE for improved
	 * performance.
	 * Uses Instant for cleaner API and avoids unnecessary casting.
	 *
	 * @param sessionKey  A session-specific key used to introduce deterministic
	 *                    randomness.
	 * @param windowStart The earliest timestamp from which posts should be
	 *                    included.
	 * @param limit       Maximum number of post IDs to return.
	 * @param offset      Number of results to skip for pagination.
	 * @return A list of post IDs ordered by the optimized scoring algorithm.
	 */
	@Query(value = """
			WITH params AS (
			    SELECT
			        EXTRACT(EPOCH FROM NOW()) AS now_epoch,
			        EXTRACT(EPOCH FROM :windowStart) AS window_epoch
			)
			SELECT p.id
			FROM post p
			CROSS JOIN params
			WHERE p.at >= :windowStart
			ORDER BY (
			        (
			            (EXTRACT(EPOCH FROM p.at) - params.window_epoch) / 3600.0
			        )
			        * EXP(-((params.now_epoch - EXTRACT(EPOCH FROM p.at)) / 86400.0))
			        * 2.0
			    )
			    + (LEAST(p.comment_count, 20) * 0.5)
			    + (((hashtext(:sessionKey || '-' || p.id) % 1000) / 1000.0) * 100.0)
			    DESC
			LIMIT :limit OFFSET :offset
			""", nativeQuery = true)
	List<Long> fetchFeedPostIdsOptimized(
			@Param("sessionKey") String sessionKey,
			@Param("windowStart") Instant windowStart,
			@Param("limit") int limit,
			@Param("offset") int offset);

	/**
	 * Performs a full-text search on validated posts using PostgreSQL's full-text
	 * search capabilities. Ranks results by relevance and supports pagination.
	 * 
	 * @param fullText The search query string, which will be processed using
	 *                 to_tsquery with the 'french' configuration.
	 * @param limit    Maximum number of post IDs to return.
	 * @param offset   Number of results to skip for pagination.
	 * @return A list of post IDs ordered by relevance.
	 */
	@Query(value = """
			SELECT p.id
			FROM post p,
			    websearch_to_tsquery('french', :fullText) AS query
			WHERE p.status = 'VALIDATED'
			    AND p.textsearchable_index_col @@ query
			ORDER BY ts_racd(p.textsearchable_index_col, query) DESC
			LIMIT :limit OFFSET :offset
			""", nativeQuery = true)
	List<Long> searchFullText(
			@Param("fullText") String fullText,
			@Param("limit") int limit,
			@Param("offset") int offset);

	/**
	 * Retrieves a post by ID with channel, files, and postReply eagerly loaded.
	 *
	 * @param id the post ID
	 * @return an Optional containing the post if found
	 */
	@EntityGraph(attributePaths = { "channel", "files", "postReply" })
	Optional<Post> findById(Long id);

	/**
	 * Retrieves posts by IDs with channel, files, and postReply eagerly loaded.
	 *
	 * @param ids the post IDs
	 * @return a list of matching posts
	 */
	@EntityGraph(attributePaths = { "channel", "files", "postReply" })
	List<Post> findAllByIdIn(List<Long> ids);

	/**
	 * Fetches posts created after a specified timestamp with channel, files, and
	 * postReply eagerly loaded. This is used for propagating new posts to
	 * followers.
	 *
	 * @param since the timestamp to filter posts
	 * @return a list of posts created after the specified timestamp
	 */
	@EntityGraph(attributePaths = { "channel", "files", "postReply" })
	@Query("SELECT p FROM Post p WHERE p.at >= :since")
	List<Post> findByAtAfter(@Param("since") Instant since);

	@Query("""
			SELECT p FROM Post p
			LEFT JOIN FETCH p.postReply
			LEFT JOIN FETCH p.channel
			LEFT JOIN FETCH p.files
			WHERE p.channel.id = :channelId
			""")
	Slice<Post> findByChannel(@Param("channelId") Long channelId, Pageable pageable);

	@Query("""
			SELECT p FROM Post p
			LEFT JOIN FETCH p.postReply
			LEFT JOIN FETCH p.channel
			LEFT JOIN FETCH p.files
			WHERE p.channel.id = :channelId AND p.visible = true
			""")
	Slice<Post> findByChannelAndVisibleTrue(@Param("channelId") Long channelId, Pageable pageable);

	/**
	 * Fetches the top 5 trending posts from the specified date, ranked by
	 * engagement and recency.
	 *
	 * Scoring: commentCount * 2.0 + reactionCount + (hours since post creation *
	 * 0.5)
	 * Recent posts with high engagement rank highest.
	 *
	 * @param since    the start date to filter posts
	 * @param pageable pagination parameters
	 * @return List of up to 5 Post objects ranked by trending score (highest
	 *         first).
	 */
	@Query("""
			SELECT p.id
			FROM Post p
			LEFT JOIN Reaction r ON r.post.id = p.id
			WHERE p.at >= :since
			GROUP BY p.id
			ORDER BY (p.commentCount * 2.0 + COUNT(r) + CAST(DATEDIFF(SECOND, p.at, CURRENT_TIMESTAMP) AS DOUBLE) * 0.5 / 3600.0) DESC
			""")
	List<Long> trendingPosts(@Param("since") Instant since, Pageable pageable);

	/**
	 * Fetches the top 5 most engaged posts from the specified date, ranked by
	 * combined discussion activity and user reactions.
	 *
	 * <p>
	 * Scoring: commentCount * 2.0 + reactionCount (descending)
	 *
	 * @param since    the start date to filter posts
	 * @param pageable pagination parameters
	 * @return List of up to 5 Post objects ranked by engagement score (highest
	 *         first).
	 */
	@Query("""
			SELECT p.id
			FROM Post p
			LEFT JOIN Reaction r ON r.post.id = p.id
			WHERE p.at >= :since
			GROUP BY p.id
			ORDER BY (p.commentCount * 2.0 + COUNT(r)) DESC, p.at DESC
			""")
	List<Long> mostEngagedPosts(@Param("since") Instant since, Pageable pageable);

	/**
	 * Recalculates and updates the comment count for all posts.
	 * 
	 * The comment count is determined by counting non-deleted comments that are
	 * directly associated with each post (excluding replies to comments).
	 * 
	 * This operation performs a single bulk update across all posts in the
	 * database.
	 * Soft-deleted comments (where deletedAt is not null) are excluded from the
	 * count.
	 * 
	 * Use this when performing a full synchronization of comment counts.
	 */
	@Modifying
	@Query("""
			UPDATE Post p
			SET p.commentCount = (
				SELECT COUNT(c.id) FROM Comment c
				WHERE c.post.id = p.id AND c.deletedAt IS NULL
			)
			""")
	void updateAllPostCommentCounts();

	/**
	 * Recalculates and updates the comment count for a set of specific posts.
	 * 
	 * The comment count is determined by counting non-deleted comments that are
	 * directly associated with each post (excluding replies to comments).
	 * 
	 * Soft-deleted comments (where deletedAt is not null) are excluded from the
	 * count.
	 * 
	 * This is useful for batch recalculations when you have multiple post IDs that
	 * need their comment counts synchronized in a single bulk operation.
	 * 
	 * Index Usage: {@code comment(post_id, deletedAt)} enables efficient
	 * filtering
	 * of comments per post while excluding soft-deleted entries.
	 * 
	 * @param postIds the IDs of posts whose comment counts should be recalculated
	 */
	@Modifying
	@Query("""
			UPDATE Post p
			SET p.commentCount = (
				SELECT COUNT(c.id) FROM Comment c
				WHERE c.post.id = p.id AND c.deletedAt IS NULL
			)
			WHERE p.id IN :postIds
			""")
	void updatePostCommentCount(@Param("postIds") Set<Long> postIds);

	/**
	 * Recalculates and updates the comment count for a specific post.
	 * 
	 * The comment count is determined by counting non-deleted comments that are
	 * directly associated with this post (excluding replies to comments).
	 * 
	 * Soft-deleted comments (where deletedAt is not null) are excluded from the
	 * count.
	 * 
	 * @param postId the ID of the post whose comment count should be recalculated
	 */
	@Modifying
	@Query("""
			UPDATE Post p
			SET p.commentCount = (
				SELECT COUNT(c.id) FROM Comment c
				WHERE c.post.id = p.id AND c.deletedAt IS NULL
			)
			WHERE p.id = :postId
			""")
	void updatePostCommentCount(@Param("postId") Long postId);

	/**
	 * Recalculates and updates the comment count for all posts that have comments
	 * in the given set of comment IDs.
	 * 
	 * When a comment is added or deleted, this method identifies its associated
	 * post(s) and recalculates their comment counts, excluding soft-deleted
	 * comments
	 * (where deletedAt is not null).
	 * 
	 * This is useful in comment lifecycle workflows where you have the IDs of
	 * affected comments and need to update their post's comment counts in a single
	 * bulk operation.
	 * 
	 * Index Usage: {@code comment(post_id, deletedAt)} enables efficient
	 * filtering
	 * of comments per post while excluding soft-deleted entries.
	 * 
	 * @param commentIds the IDs of comments that were added or deleted, used to
	 *                   find their associated posts
	 */
	@Modifying
	@Query("""
			UPDATE Post p
			SET p.commentCount = (
				SELECT COUNT(c.id) FROM Comment c
				WHERE c.post.id = p.id AND c.deletedAt IS NULL
			)
			WHERE p.id IN (
				SELECT c.post.id FROM Comment c WHERE c.id IN :commentIds
			)
			""")
	void updatePostCommentCountByCommentIds(@Param("commentIds") Set<Long> commentIds);

}
