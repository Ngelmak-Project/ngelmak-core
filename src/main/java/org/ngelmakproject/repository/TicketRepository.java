package org.ngelmakproject.repository;

import java.util.Optional;

import org.ngelmakproject.domain.Ticket;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the Ticket entity.
 */
@SuppressWarnings("unused")
@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long> {
  @EntityGraph(attributePaths = { "evidence", "post.files", "comment.file", "channel" })
  Optional<Ticket> findById(Long id);
}
