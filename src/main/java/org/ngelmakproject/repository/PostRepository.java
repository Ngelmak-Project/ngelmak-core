package org.ngelmakproject.repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.ngelmakproject.domain.Channel;
import org.ngelmakproject.domain.Post;
import org.ngelmakproject.domain.Post.Status;
import org.ngelmakproject.repository.projection.CommentProjection;
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
	Optional<Post> findById(Long id);

	Optional<PostProjection> findProjectedById(Long id);

	@Modifying
	@Query("UPDATE Post p SET p.deletedAt = :ts WHERE p.id IN :ids")
	int softDeleteByIds(List<Long> ids, @Param("ts") Instant ts);

	@Query("SELECT p FROM Post p WHERE p.deletedAt < :cutoff")
	List<PostProjection> findExpiredPosts(Instant cutoff);

	// @Query("""
	// SELECT pf.file.id FROM
	// Post p
	// LEFT JOIN
	// FETCH p.files pf
	// WHERE p.deletedAt<:cutoff
	// """)
	// List<Long> findFileIdsForExpiredPosts(Instant cutoff);

	@Query(value = """
			SELECT p.*,
			(
			(EXTRACT(EPOCH FROM p.created_at) * 0.8) +
			(LEAST(p.comment_count, 20) * 0.2) +
			((hashtext(CONCAT(:sessionKey, '-', p.id)) % 1000) / 1000.0 * 300)
			) AS score
			FROM nk_post p
			WHERE p.created_at >= :windowStart
			ORDER BY score DESC
			LIMIT :limit OFFSET :offset
			""", nativeQuery = true)
	List<Post> fetchFeed(
			@Param("sessionKey") String sessionKey,
			@Param("windowStart") Instant windowStart,
			@Param("limit") int limit,
			@Param("offset") int offset);

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
			FROM nk_post p
			LEFT JOIN nk_channel c ON c.id = p.channel_id
			LEFT JOIN nk_post r ON r.post_reply_id = p.id
			LEFT JOIN nk_post_file pf ON pf.post_id = p.id
			LEFT JOIN nk_file f ON f.id = pf.file_id
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
			FROM nk_post p
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
	List<Long> fetchFeedPostIds(
			@Param("sessionKey") String sessionKey,
			@Param("windowStart") Instant windowStart,
			@Param("limit") int limit,
			@Param("offset") int offset);

	/**
	 * Fetches a list of post IDs for the user feed using epoch-based window start.
	 * Applies a scoring algorithm combining recency, exponential decay, comment
	 * count, and session-based randomization.
	 *
	 * @param sessionKey       A session-specific key used to introduce
	 *                         deterministic randomness.
	 * @param windowStartEpoch Epoch seconds representing the earliest allowed
	 *                         timestamp.
	 * @param limit            Maximum number of post IDs to return.
	 * @param offset           Number of results to skip for pagination.
	 * @return A list of post IDs ordered by the computed feed score.
	 */
	@Query(value = """
			SELECT p.id
			FROM nk_post p
			WHERE p.at >= TO_TIMESTAMP(:windowStartEpoch)
			ORDER BY (
			        (
			            (EXTRACT(EPOCH FROM p.at) - :windowStartEpoch) / 3600.0
			        )
			        * EXP(-((EXTRACT(EPOCH FROM NOW()) - EXTRACT(EPOCH FROM p.at)) / 86400.0))
			        * 2.0
			    )
			    + (LEAST(p.comment_count, 20) * 0.5)
			    + (((hashtext(:sessionKey || '-' || p.id) % 1000) / 1000.0) * 100.0)
			    DESC
			LIMIT :limit OFFSET :offset
			""", nativeQuery = true)
	List<Long> fetchFeedPostIds(
			@Param("sessionKey") String sessionKey,
			@Param("windowStartEpoch") long windowStartEpoch,
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
			FROM nk_post p
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
			FROM nk_post p,
			    websearch_to_tsquery('french', :fullText) AS query
			WHERE p.status = 'VALIDATED'
			    AND p.textsearchable_index_col @@ query
			ORDER BY ts_rank_cd(p.textsearchable_index_col, query) DESC
			LIMIT :limit OFFSET :offset
			""", nativeQuery = true)
	List<Long> searchFullText(
			@Param("fullText") String fullText,
			@Param("limit") int limit,
			@Param("offset") int offset);

	@EntityGraph(attributePaths = { "channel", "files", "postReply" })
	List<Post> findAllByIdIn(List<Long> ids);

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
			WHERE p.channel.id = :channelId AND p.status = :status
			""")
	Slice<Post> findByChannelAndStatus(@Param("channelId") Long channelId, @Param("status") Status status,
			Pageable pageable);

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
	 * Use an @EntityGraph to fetch channel + files in one go:
	 * 
	 * @param status
	 * @param pageable
	 * @return
	 */
	@EntityGraph(attributePaths = { "channel", "files" })
	Slice<Post> findByStatusOrderByAtDesc(Status status, Pageable pageable);

	@Query("""
			SELECT p FROM Post p
			LEFT JOIN FETCH p.channel
			LEFT JOIN FETCH p.files
			WHERE p.status = 'PUBLISHED'
			""")
	Slice<Post> findAllWithRelations(Pageable pageable);

	@Modifying
	@Query("""
			UPDATE Post p
			SET p.commentCount = (SELECT COUNT(c.id) FROM Comment c
			WHERE c.post.id = p.id)
			""")
	void updateAllPostCommentCounts();

	@Modifying
	@Query("""
			UPDATE Post p
			SET p.commentCount=(SELECT COUNT(c.id) FROM Comment c
			WHERE c.post.id = :postId)
			""")
	void updatePostCommentCount(@Param("postId") Long postId);

	@Modifying
	@Query("""
			UPDATE Post p
			SET p.commentCount = GREATEST(0, p.commentCount + :countChange)
			WHERE p.id = :postId
			""")
	void updatePostCommentCount(@Param("postId") Long postId, @Param("countChange") Integer countChange);
}
