package org.ngelmakproject.repository;

import java.time.Instant;
import java.util.List;

import org.ngelmakproject.domain.Event;
import org.ngelmakproject.domain.Event.EventStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EventRepository extends JpaRepository<Event, Long> {

    List<Event> findByStatus(EventStatus status);

    List<Event> findByCreatedByUser(Long userId);

    List<Event> findByLocationsId(Long locationId);

    List<Event> findByTitleContainingIgnoreCase(String title);

    List<Event> findByTitleContainingIgnoreCaseAndLocationsIdAndStatus(String title, Long locationId, EventStatus status);

    List<Event> findByCreatedAtBetween(Instant startDate, Instant endDate);

    List<Event> findAllByOrderByCreatedAtDesc();

    long countByStatus(EventStatus status);

    long countByCreatedByUser(Long userId);

    long countByLocationsId(Long locationId);
}

