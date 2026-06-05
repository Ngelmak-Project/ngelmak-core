package org.ngelmakproject.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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
@Repository
@SuppressWarnings("unused")
public interface CommentRepository extends JpaRepository<Comment, Long> {
	/**
	 * Finds top-level comments for a given post, ordered by creation time in
	 * descending order.
	 * 
	 * @param postId   the ID of the post to find comments for
	 * @param pageable the pagination information
	 * @return the slice of top-level comments
	 */
	@Query("""
			SELECT c FROM Comment c
			LEFT JOIN FETCH c.post
			LEFT JOIN FETCH c.channel
			LEFT JOIN FETCH c.file
			WHERE c.post.id = :postId AND c.replyTo IS NULL AND c.deletedAt IS NULL
			ORDER BY c.at DESC
			""")
	Slice<Comment> findTopLevelCommentsByPost(@Param("postId") Long postId, Pageable pageable);

	/**
	 * Finds top-level comments for a given channel, ordered by creation time in
	 * descending order.
	 * 
	 * @param channelId the ID of the channel to find comments for
	 * @param pageable  the pagination information
	 * @return the slice of top-level comments
	 */
	@Query("""
			SELECT c FROM Comment c
			LEFT JOIN FETCH c.post
			LEFT JOIN FETCH c.channel
			LEFT JOIN FETCH c.file
			WHERE c.channel.id = :channelId AND c.deletedAt IS NULL
			ORDER BY c.at DESC
			""")
	Slice<Comment> findCommentsByChannelOrByAtDesc(@Param("channelId") Long channelId, Pageable pageable);

	/**
	 * Finds replies to a specific comment, ordered by creation time in ascending
	 * order.
	 * 
	 * @param commentId the ID of the comment to find replies for
	 * @return the list of replies to the specified comment
	 */
	@Query("""
			SELECT c FROM Comment c
			LEFT JOIN FETCH c.channel
			LEFT JOIN FETCH c.file
			WHERE c.replyTo.id = :commentId AND c.deletedAt IS NULL
			ORDER BY c.at ASC
			""")
	List<Comment> findRepliesByComment(@Param("commentId") Long commentId);

	/**
	 * Finds comments by a set of IDs, including their associated post, channel, and
	 * file entities.
	 * 
	 * @param ids the set of comment IDs to find
	 * @return the list of comments matching the specified IDs
	 */
	@Modifying
	@Query("""
			UPDATE Comment c
			SET c.replyCount = GREATEST(0, c.replyCount + :countChange)
			WHERE c.id = :commentId
			""")
	void updateReplyCount(@Param("commentId") Long commentId, @Param("countChange") Long countChange);

	/**
	 * <p>
	 * Recalculates and updates the display reply count for all comments.
	 * 
	 * The reply count is determined by counting non-deleted replies that directly
	 * reference each comment via the {@code replyTo} relationship.
	 * 
	 * This operation performs a single bulk update across all comments in the
	 * database.
	 * Soft-deleted replies (where deletedAt is not null) are excluded from the
	 * count.
	 * 
	 * Use this when performing a full synchronization of reply counts.
	 */
	@Modifying
	@Query("""
			UPDATE Comment c
			SET c.replyCount = (
				SELECT COUNT(c2.id) FROM Comment c2
				WHERE c2.replyTo.id = c.id AND c2.deletedAt IS NULL
			)
			""")
	void updateAllReplyCounts();

	/**
	 * <p>
	 * Recalculates and updates the display reply count for a specific comment.
	 * 
	 * The reply count is determined by counting non-deleted replies that directly
	 * reference this comment via the {@code replyTo} relationship.
	 * 
	 * Soft-deleted replies (where deletedAt is not null) are excluded from the
	 * count.
	 * 
	 * @param commentId the ID of the comment whose reply count should be
	 *                  recalculated
	 */
	@Modifying
	@Query("""
			UPDATE Comment c
			SET c.replyCount = (
				SELECT COUNT(c2.id) FROM Comment c2
				WHERE c2.replyTo.id = :commentId AND c2.deletedAt IS NULL
			)
			WHERE c.id = :commentId
			""")
	void updateReplyCount(@Param("commentId") Long commentId);

	/**
	 * <p>
	 * Recalculates and updates the display reply count for the given comments.
	 * 
	 * The reply count is determined by counting non-deleted replies that directly
	 * reference this comment via the {@code replyTo} relationship.
	 * 
	 * This operation performs a single bulk update in the database, making it
	 * efficient for batch recalculations. Soft-deleted replies (where deletedAt
	 * is not null) are excluded from the count.
	 * 
	 * @param commentIds the IDs of comments whose reply counts should be
	 *                   recalculated
	 */
	@Modifying
	@Query("""
			UPDATE Comment c
			SET c.replyCount = (
				SELECT COUNT(c2.id) FROM Comment c2
				WHERE c2.replyTo.id = c.id AND c2.deletedAt IS NULL
			)
			WHERE c.id IN :commentIds
			""")
	void updateReplyCount(@Param("commentIds") Set<Long> commentIds);

	/**
	 * Recalculates and updates the display reply count for all comments that have
	 * direct replies in the given set of deleted reply IDs.
	 * 
	 * When a reply is deleted, this method identifies its parent comment(s) via the
	 * {@code replyTo} relationship and recalculates their reply counts, excluding
	 * soft-deleted replies (where deletedAt is not null).
	 * 
	 * This is useful in delete workflows where you only have the IDs of the deleted
	 * replies and need to update the reply counts of their parent comments in a
	 * single bulk operation.
	 * 
	 * @param replyIds the IDs of replies that were deleted, used to find their
	 *                 parent comments
	 */
	@Modifying
	@Query("""
			UPDATE Comment c
			SET c.replyCount = (
				SELECT COUNT(c2.id) FROM Comment c2
				WHERE c2.replyTo.id = c.id AND c2.deletedAt IS NULL
			)
			WHERE c.id IN (
				SELECT c2.replyTo.id FROM Comment c2 WHERE c2.id IN :replyIds
			)
			""")
	void updateReplyCountByReplyIds(@Param("replyIds") Set<Long> replyIds);

	/**
	 * Soft-deletes a comment by its ID.
	 *
	 * @param id the ID of the comment to soft-delete
	 * @param ts the timestamp to set as the deletion time
	 * @return the number of comments that were soft-deleted (0 or 1)
	 */
	@Modifying
	@Query("UPDATE Comment c SET c.deletedAt = :ts WHERE c.id = :id")
	int softDeleteById(@Param("id") Long id, @Param("ts") Instant ts);

	/**
	 * Soft-deletes comments by their IDs.
	 *
	 * @param ids the IDs of the comments to soft-delete
	 * @param ts  the timestamp to set as the deletion time
	 * @return the number of comments that were soft-deleted
	 */
	@Modifying
	@Query("UPDATE Comment c SET c.deletedAt = :ts WHERE c.id IN :ids")
	int softDeleteByIds(Set<Long> ids, @Param("ts") Instant ts);

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

	/**
	 * Finds comments that have been soft-deleted and are past the expiration
	 * cutoff.
	 *
	 * @param cutoff the timestamp before which soft-deleted comments are considered
	 *               expired
	 * @return a list of comment projections for expired comments
	 */
	@Query("SELECT c FROM Comment c WHERE c.deletedAt < :cutoff")
	List<CommentProjection> findExpiredComments(Instant cutoff);

	/**
	 * Finds a comment by its ID and returns it as a projection.
	 *
	 * @param id the ID of the comment to find
	 * @return an Optional containing the comment projection if found, or empty if
	 *         not found
	 */
	Optional<CommentProjection> findProjectedById(Long id);
}
