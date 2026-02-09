package org.ngelmakproject.repository;

import java.util.List;

import org.ngelmakproject.domain.Channel;
import org.ngelmakproject.domain.Feed;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the Feed entity.
 */
@SuppressWarnings("unused")
@Repository
public interface FeedRepository extends JpaRepository<Feed, Long> {
  Slice<Feed> findByFeedOwnerIn(List<Channel> feedOwners, Pageable pageable);

  @EntityGraph(attributePaths = { "post", "post.channel", "post.files" })
  Slice<Feed> findByFeedOwner(Channel feedOwner, Pageable pageable);
}
