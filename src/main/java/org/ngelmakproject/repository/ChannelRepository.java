package org.ngelmakproject.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.ngelmakproject.domain.Channel;
import org.ngelmakproject.repository.projection.ChannelProjection;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data JPA repository for the Channel entity.
 */
@SuppressWarnings("unused")
public interface ChannelRepository extends JpaRepository<Channel, Long> {
	Optional<Channel> findOneByUser(Long id);

	/**
	 * Retrieves a channel by its unique identifier.
	 *
	 * @param identifier the unique identifier of the channel
	 * @return an Optional containing the Channel if found, or empty if not found
	 */
	Optional<Channel> findOneByIdentifier(String identifier);

	/**
	 * Fetches a channel by its database id.
	 * Also returns the total number of posts for that channel.
	 */
	@Query("""
			SELECT new org.ngelmakproject.repository.projection.ChannelProjection(
				c.id AS id,
				c.name AS name,
				c.identifier AS identifier,
				c.avatar AS avatar,
				c.banner AS banner,
				c.description AS description,
				c.createdAt AS createdAt,
				(SELECT COUNT(p.id) FROM Post p WHERE p.channel.id = c.id) AS postCount
			)
			FROM Channel c
			WHERE c.id = :id
			""")
	Optional<ChannelProjection> findChannelById(@Param("id") Long id);

	/**
	 * Fetches a channel by its unique identifier.
	 * Also returns the total number of posts for that channel.
	 */
	@Query("""
			SELECT new org.ngelmakproject.repository.projection.ChannelProjection(
				c.id AS id,
				c.name AS name,
				c.identifier AS identifier,
				c.avatar AS avatar,
				c.banner AS banner,
				c.description AS description,
				c.createdAt AS createdAt,
				(SELECT COUNT(p.id) FROM Post p WHERE p.channel.id = c.id) AS postCount
			)
			FROM Channel c
			WHERE c.identifier = :identifier
			""")
	Optional<ChannelProjection> findChannelByIdentifier(@Param("identifier") String identifier);

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
			SELECT new org.ngelmakproject.repository.projection.ChannelProjection(
				c.id AS id,
				c.name AS name,
				c.identifier AS identifier,
				c.avatar AS avatar,
				c.banner AS banner,
				c.description AS description,
				c.createdAt AS createdAt,
				COUNT(p.id) AS postCount
			)
			FROM Channel c
			LEFT JOIN Post p
			ON p.channel.id = c.id AND p.at >= :since
			GROUP BY
				c.id, c.name, c.identifier, c.avatar,
				c.banner, c.description, c.createdAt
			HAVING COUNT(p.id) > 0
			ORDER BY COUNT(p.id) DESC, c.createdAt ASC
			""")
	List<ChannelProjection> topActiveChannels(@Param("since") Instant since, Pageable pageable);

	/**
	 * Soft-deletes channel by ID.
	 *
	 * @param id the ID of the channel to soft-delete
	 * @return the number of channels that were soft-deleted
	 */
	@Modifying
	@Query("UPDATE Channel c SET c.deletedAt = :ts WHERE c.id = :id")
	int softDeleteById(Long id, Instant ts);
}
