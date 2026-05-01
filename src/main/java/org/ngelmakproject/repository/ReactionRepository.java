package org.ngelmakproject.repository;

import java.util.List;
import java.util.Optional;

import org.ngelmakproject.domain.Reaction;
import org.ngelmakproject.domain.Reaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the Reaction entity.
 */
@SuppressWarnings("unused")
@Repository
public interface ReactionRepository extends JpaRepository<Reaction, Long> {
  @Query("SELECT r FROM Reaction r WHERE r.post.id IN :postIds")
  List<Reaction> findByPostIds(List<Long> postIds);

  @Query("SELECT r FROM Reaction r WHERE r.post.id IN :postIds and r.channel.id IN :channelIds")
  List<Reaction> findByPostInAndChannelIn(List<Long> postIds, List<Long> channelIds);
}
