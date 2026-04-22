package org.ngelmakproject.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Queue;

import org.ngelmakproject.domain.Channel;
import org.ngelmakproject.repository.projection.ActiveChannelProjection;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the Channel entity.
 */
@SuppressWarnings("unused")
@Repository
public interface ChannelRepository extends JpaRepository<Channel, Long> {
    Optional<Channel> findOneByUser(Long id);

    Optional<Channel> findOneByIdentifier(String identifier);

    Boolean existsByIdentifier(String identifier);

    /**
     * Retrieves the top 10 most active channels based on post activity in the last
     * 7 days.
     * 
     * Results are ordered by:
     * 1. Post count in descending order (most active first)
     * 2. Channel creation date in ascending order (oldest channels first, as
     * tiebreaker)
     * 
     * @return List of active channel projections containing channel ID and post
     *         count. Returns an empty list if no channels exist.
     */
    @Query("""
            SELECT c.id AS id,
                    c.name AS name,
                    c.identifier AS identifier,
                    c.avatar AS avatar,
                    c.banner AS banner,
                    c.description AS description,
                    COUNT(p.id) AS postCount
            FROM Channel c
            LEFT JOIN Post p
            ON p.channel.id = c.id AND p.at >= :since
            GROUP BY c.id
            ORDER BY COUNT(p.id)
            DESC, c.createdAt ASC
            """)
    List<ActiveChannelProjection> topActiveChannels(@Param("since") Instant since, Pageable pageable);

}
