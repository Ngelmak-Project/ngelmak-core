package org.ngelmakproject.repository;

import java.time.Instant;
import java.time.Instant;
import java.time.Instant;
import java.util.List;

import org.antlr.v4.runtime.atn.SemanticContext.AND;
import org.ngelmakproject.domain.Notification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the Notification entity.
 */
@SuppressWarnings("unused")
@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
	@Query("""
			SELECT n FROM Notification n
			WHERE n.expiresAt >= :now
			ORDER BY function('RANDOM')
			""")
	List<Notification> findActiveRandom(Instant now, Pageable pageable);
}