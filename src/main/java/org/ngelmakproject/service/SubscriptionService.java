package org.ngelmakproject.service;

import java.util.List;

import org.ngelmakproject.domain.Channel;
import org.ngelmakproject.domain.Subscription;
import org.ngelmakproject.repository.SubscriptionRepository;
import org.ngelmakproject.web.rest.dto.SubscriptionDTO;
import org.ngelmakproject.web.rest.dto.SubscriptionStatsDTO;
import org.ngelmakproject.web.rest.errors.ChannelNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service Implementation for managing
 * {@link org.ngelmakproject.domain.Subscription}.
 */
@Service
@Transactional
public class SubscriptionService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionService.class);

    private final ChannelService channelService;
    private final SubscriptionRepository subscriptionRepository;

    public SubscriptionService(ChannelService channelService,
            SubscriptionRepository subscriptionRepository) {
        this.channelService = channelService;
        this.subscriptionRepository = subscriptionRepository;
    }

    /**
     * Follows the target channel on behalf of the current user.
     *
     * <p>
     * If a subscription already exists, it is returned as-is.
     * Otherwise, a new subscription is created and returned.
     * </p>
     *
     * @param subscribedTo the channel to follow
     * @return the existing or newly created Subscription
     * @throws ChannelNotFoundException if the current user's channel
     *                                  or the target channel cannot be found
     */
    public Subscription followChannel(Channel subscribedTo) {
        log.debug("Request to follow Channel : {}", subscribedTo);

        // Load the channel associated with the current authenticated user
        Channel subscriber = channelService.findOneByCurrentUser()
                .orElseThrow(ChannelNotFoundException::new);

        // Return the existing subscription if it already exists
        return subscriptionRepository
                .findBySubscriberAndSubscribedTo(subscriber, subscribedTo)
                .orElseGet(() -> {
                    // Create and save a new subscription
                    Subscription newSubscription = new Subscription();
                    newSubscription.setSubscriber(subscriber);
                    newSubscription.setSubscribedTo(subscribedTo);
                    return subscriptionRepository.save(newSubscription);
                });
    }

    /**
     * Removes the subscription between the current user and the target channel.
     *
     * <p>
     * If no subscription exists, nothing happens. The method is idempotent:
     * calling it multiple times produces the same result.
     * </p>
     *
     * @param targetChannelId the ID of the channel to unfollow
     * @return the current user's channel
     * @throws ChannelNotFoundException if the current user's channel cannot be
     *                                  found
     */
    public void unfollowUser(Long targetChannelId) {
        log.debug("Request to unfollow Channel : {}", targetChannelId);
        // Load the channel associated with the current authenticated user
        Channel currentChannel = channelService.findOneByCurrentUser()
                .orElseThrow(ChannelNotFoundException::new);

        // Look for an existing subscription and delete it if present
        subscriptionRepository
                .findByFollowingAndFollower(targetChannelId, currentChannel.getId())
                .ifPresent(subscriptionRepository::delete);

        log.debug("Subscription removed for follower {} from channel {}", currentChannel.getId(), targetChannelId);
    }

    /**
     * Builds a complete subscription statistics view for the given channel.
     *
     * <p>
     * This method retrieves all subscriptions where the channel appears,
     * separates them into followers and following, and returns a structured
     * DTO that can be used to compute counts, lists, or additional analytics.
     * </p>
     *
     * @param channelId the ID of the channel to analyze
     * @return a DTO containing all subscription relations for the channel
     * @throws ChannelNotFoundException if the channel does not exist
     */
    @Transactional(readOnly = true)
    public SubscriptionStatsDTO getSubscriptionStatistics(Long channelId) {
        log.debug("Request to get Subscriptions : {}", channelId);

        // Fetch all subscriptions where this channel appears
        List<Subscription> all = subscriptionRepository.findAllByChannelInvolved(channelId);

        // Split into followers and following
        List<SubscriptionDTO> followers = all.stream()
                .filter(s -> s.getSubscribedTo().getId().equals(channelId))
                .map(SubscriptionDTO::from)
                .toList();

        List<SubscriptionDTO> following = all.stream()
                .filter(s -> s.getSubscriber().getId().equals(channelId))
                .map(SubscriptionDTO::from)
                .toList();

        // Build the DTO
        return new SubscriptionStatsDTO(
                channelId,
                followers.size(),
                following.size(),
                followers,
                following);
    }

}
