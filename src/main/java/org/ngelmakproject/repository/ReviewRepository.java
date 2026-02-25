package org.ngelmakproject.repository;

import java.util.List;

import org.ngelmakproject.domain.Review;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the Review entity.
 */
@SuppressWarnings("unused")
@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {
  List<Review> findByTicketIdOrderByCreatedAtDesc(Long ticketId);
}
