package org.ngelmakproject.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.ngelmakproject.domain.Comment;
import org.ngelmakproject.repository.projection.CommentProjection;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the Comment entity.
 */
@SuppressWarnings("unused")
@Repository
public interface CommentRepository extends JpaRepository<Comment, Long> {
	// @Query("""
	// SELECT c FROM Comment c
	// LEFT JOIN FETCH c.channel
	// LEFT JOIN FETCH c.file
	// WHERE c.post.id = :id
	// AND c.deletedAt IS NULL
	// ORDER BY c.at
	// """)
	// Slice<Comment> findByPostOrderByAt(@Param("id") Long id, Pageable
	// pageable);

	// @Query("""
	// select new Comment(
	// c.id,
	// c.opinion,
	// c.at,
	// c.lastUpdate,
	// c.deletedAt,
	// c.content,
	// c.url,
	// c.post,
	// c.replayto,
	// c.channel
	// )
	// from Comment c
	// where c.id = :id
	// """)
	// Optional<Comment> findById(@Param("id") Long id);

	// @Modifying
	// @Query("update Comment c set c.content = :content and c.url = :url where
	// u.id < :id")
	// void update(@Param("id") Long id, @Param("content") String content,
	// @Param("url") String url);

	@Query(value = """
			SELECT c.*
			FROM nk_post p
			JOIN LATERAL (
			    SELECT *
			    FROM nk_comment c
			    WHERE c.post_id = p.id
			    ORDER BY c.at DESC
			    LIMIT :limit
			) c ON TRUE
			WHERE p.id IN :postIds
			ORDER BY p.id, c.at DESC
			""", nativeQuery = true)
	List<Comment> findTopCommentsForPosts(@Param("postIds") List<Long> postIds, @Param("limit") Integer limit);

	@Query("""
			SELECT c FROM Comment c
			LEFT JOIN FETCH c.post
			LEFT JOIN FETCH c.channel
			LEFT JOIN FETCH c.file
			WHERE c.post.id = :postId AND c.replyTo IS NULL AND c.deletedAt IS NULL
			ORDER BY c.at DESC
			""")
	Slice<Comment> findTopLevelCommentsByPost(@Param("postId") Long postId, Pageable pageable);

	@Query("""
			SELECT c FROM Comment c
			LEFT JOIN FETCH c.post
			LEFT JOIN FETCH c.channel
			LEFT JOIN FETCH c.file
			WHERE c.channel.id = :channelId AND c.deletedAt IS NULL
			ORDER BY c.at DESC
			""")
	Slice<Comment> findCommentsByChannelOrByAtDesc(@Param("channelId") Long channelId, Pageable pageable);

	@Query("""
			SELECT c FROM Comment c
			LEFT JOIN FETCH c.channel
			LEFT JOIN FETCH c.file
			WHERE c.replyTo.id = :commentId AND c.deletedAt IS NULL
			ORDER BY c.at ASC
			""")
	List<Comment> findRepliesByComment(@Param("commentId") Long commentId);

	@Modifying
	@Query("""
			UPDATE Comment c
			SET c.replyCount = (SELECT COUNT(c.id) FROM Comment c2
			WHERE c2.replyTo.id = c.id)
			""")
	void updateAllReplyCounts();

	@Modifying
	@Query("""
			  UPDATE Comment c
			  SET c.replyCount=(SELECT COUNT(c.id) FROM Comment c2
			  WHERE c2.replyTo.id = :commentId)
			""")
	void updateReplyCount(@Param("commentId") Long commentId);

	@Modifying
	@Query("""
			UPDATE Comment c
			SET c.replyCount = GREATEST(0, c.replyCount + :countChange)
			WHERE c.id = :commentId
			""")
	void updateReplyCount(@Param("commentId") Long commentId, @Param("countChange") Integer countChange);

	@Modifying
	@Query("UPDATE Comment c SET c.deletedAt = :ts WHERE c.id = :id")
	int softDeleteById(@Param("id") Long id, @Param("ts") Instant ts);

	@Modifying
	@Query("UPDATE Comment c SET c.deletedAt = :ts WHERE c.id IN :ids")
	int softDeleteByIds(List<Long> ids, @Param("ts") Instant ts);

	@Modifying
	@Query("""
			    UPDATE Comment c
			    SET c.deletedAt = :ts
			    WHERE c.id = :id AND c.channel.id = :channelId
			""")
	int softDeleteByIdAndChannel(@Param("id") Long id, @Param("channelId") Long channelId, @Param("ts") Instant ts);

	@Modifying
	@Query("DELETE FROM Comment c WHERE c.deletedAt < :cutoff")
	int deleteExpiredComments(Instant cutoff);

	@Query("SELECT c FROM Comment c WHERE c.deletedAt < :cutoff")
	List<CommentProjection> findExpiredComments(Instant cutoff);

	@EntityGraph(attributePaths = { "post", "replyTo" })
	Optional<Comment> findWithPostAndReplyToById(Long id);

	Optional<CommentProjection> findProjectedById(Long id);

}
