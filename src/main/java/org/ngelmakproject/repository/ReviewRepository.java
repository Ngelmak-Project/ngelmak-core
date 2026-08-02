package org.ngelmakproject.repository;

import java.util.List;

import org.ngelmakproject.domain.Review;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for the Review entity.
 */
@SuppressWarnings("unused")
public interface ReviewRepository extends JpaRepository<Review, Long> {
  List<Review> findByTicketIdOrderByCreatedAtAsc(Long ticketId);
}
