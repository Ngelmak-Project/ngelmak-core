package org.ngelmakproject.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.ngelmakproject.domain.Channel;
import org.ngelmakproject.domain.Post;
import org.ngelmakproject.domain.Post.Status;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the Post entity.
 */
@SuppressWarnings("unused")
@Repository
public interface PostRepository extends JpaRepository<Post, Long> {
	// @Query(value = "SELECT " +
	// " full_search.*, " +
	// " p.id AS post_reference_id, " +
	// " p.title AS post_reference_title, " +
	// " p.content AS post_reference_content, " +
	// " a.name AS channel_name " +
	// "FROM ( " +
	// " SELECT p.* FROM ( " +
	// " SELECT *, ts_rank_cd(textsearchable_index_col, query) AS rank " +
	// " FROM nk_post, to_tsquery('french', :fullText) query " +
	// " WHERE status = 'VALIDATED' AND textsearchable_index_col @@ query " +
	// " ) AS p " +
	// " LEFT JOIN (SELECT id, ts_rank_cd(textsearchable_index_col, query) AS rank "
	// +
	// " FROM nk_post, to_tsquery('french', :fullText) query " +
	// " WHERE textsearchable_index_col @@ query) AS a " +
	// " ON p.channel_id = a.id " +
	// " ORDER BY a.rank,p.rank DESC " +
	// " LIMIT :limit " +
	// " OFFSET :offset " +
	// ") AS full_search " +
	// "LEFT JOIN nk_post AS p ON full_search.post_reference_id = p.id " +
	// "LEFT JOIN nk_channel AS a ON a.id = p.channel_id", nativeQuery = true)
	// List<Tuple> fullTextSearch(@Param("fullText") String fullText,
	// @Param("limit") Integer limit,
	// @Param("offset") Long offset);
	// JOIN FETCH post.comments comments JOIN FETCH post.attachments attachments

	// @Query("SELECT post FROM Post post " +
	// "LEFT JOIN FETCH post.channel channel " +
	// "LEFT JOIN FETCH post.postReply postReply " +
	// "LEFT JOIN FETCH post.comments comments " +
	// "LEFT JOIN FETCH post.attachments attachments " +
	// "WHERE post.id = ?1")
	Optional<Post> findById(Long id);

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
			           (EXTRACT(EPOCH FROM p.at) * 0.8) +
			           (LEAST(p.comment_count, 20) * 0.2) +
			           ((hashtext(CONCAT(:sessionKey, '-', p.id)) % 1000) / 1000.0 * 300)
			       ) AS score
			FROM nk_post p
			LEFT JOIN nk_channel c ON c.id = p.channel_id
			LEFT JOIN nk_post r ON r.post_reply_id = p.id
			WHERE p.at >= :windowStart
			ORDER BY score DESC
			LIMIT :limit OFFSET :offset
			""", nativeQuery = true)
	List<Post> fetchFeedWithRelations(
			@Param("sessionKey") String sessionKey,
			@Param("windowStart") Instant windowStart,
			@Param("limit") int limit,
			@Param("offset") int offset);

	@Query("""
			SELECT p FROM Post p
			LEFT JOIN FETCH p.postReply
			LEFT JOIN FETCH p.channel
			LEFT JOIN FETCH p.files
			WHERE p.channel.id = :channelId
			""")
	Slice<Post> findByChannel(@Param("channelId") Long channelId,
			Pageable pageable);

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
