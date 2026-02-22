package org.ngelmakproject.repository;

import java.util.Optional;

import org.ngelmakproject.domain.Ticket;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Spring Data JPA repository for the Ticket entity.
 */
@SuppressWarnings("unused")
@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long> {
  @EntityGraph(attributePaths = { "evidence", "post.files", "comment.file", "channel" })
  Optional<Ticket> findById(Long id);

  /**
   * Retrieves the ID of the user who owns the channel associated with one of the
   * following entities:
   *
   * <ul>
   * <li>The channel itself (identified by {@code channelId})</li>
   * <li>A post (identified by {@code postId}), by resolving the post's
   * channel</li>
   * <li>A comment (identified by {@code commentId}), by resolving the comment's
   * channel</li>
   * </ul>
   *
   * <p>
   * This method is useful when you need to determine the owner of a channel
   * based on different types of content (channel, post, or comment) without
   * knowing in advance which identifier is available.
   * </p>
   *
   * <p>
   * Null parameters are safely ignored. Only non-null parameters participate
   * in the query conditions. If multiple parameters are provided, the first
   * matching condition will return the associated channel's user.
   * </p>
   *
   * <p>
   * The query returns an {@link Optional} containing the user ID (a {@code Long})
   * if a matching channel is found, or an empty {@code Optional} otherwise.
   * </p>
   *
   * @param channelId the ID of the channel, or {@code null} if not applicable
   * @param postId    the ID of the post whose channel owner should be resolved,
   *                  or {@code null}
   * @param commentId the ID of the comment whose channel owner should be
   *                  resolved, or {@code null}
   * @return an {@code Optional<Long>} containing the channel owner's user ID, if
   *         found
   */
  @Query("""
      SELECT c.user
      FROM Channel c
      WHERE (:channelId IS NOT NULL AND c.id = :channelId)
         OR (:postId IS NOT NULL AND c.id = (SELECT p.channel.id FROM Post p WHERE p.id = :postId))
         OR (:commentId IS NOT NULL AND c.id = (SELECT cm.channel.id FROM Comment cm WHERE cm.id = :commentId))
      """)
  Optional<Long> getUserIdByChannelOrPostOrComment(
      @Param("channelId") Long channelId,
      @Param("postId") Long postId,
      @Param("commentId") Long commentId);

}
