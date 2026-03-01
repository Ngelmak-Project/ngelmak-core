package org.ngelmakproject.repository;

import java.util.List;
import java.util.Optional;

import org.ngelmakproject.domain.Channel;
import org.ngelmakproject.domain.Subscription;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the Subscription entity.
 */
@SuppressWarnings("unused")
@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {
	@Query("SELECT s FROM Subscription s WHERE s.subscribeTo.id = :subscribeTo AND s.subscriber.id = :subscriber")
	Optional<Subscription> findBySubscriberAndSubscribedTo(
			@Param("subscriber") Long subscriber, @Param("subscribeTo") Long subscribeTo);

	Optional<Subscription> findBySubscriberAndSubscribedTo(Channel subscriber, Channel subscribeTo);

	/**
	 * Retrieves all subscriptions where the given channel appears either
	 * as the subscriber or as the channel being followed.
	 *
	 * @param channelId the ID of the channel to search for
	 * @return all matching Subscription entities
	 */
	@Query("""
			SELECT s FROM Subscription s
			WHERE s.subscribedTo.id = :channelId
			   OR s.subscriber.id = :channelId
			""")
	List<Subscription> findAllByChannelInvolved(@Param("channelId") Long channelId);

	List<Subscription> findBySubscribedTo(Channel subscribeTo);

	@Query("SELECT s FROM Subscription s WHERE s.subscribedTo.id = :channelId")
	List<Subscription> findBySubscribedTo(@Param("channelId") Long channelId);

	List<Subscription> findBySubscriber(Channel subscriber);
}
