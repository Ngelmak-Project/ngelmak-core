package org.ngelmakproject.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.ngelmakproject.domain.Channel;
import org.ngelmakproject.domain.Subscription;
import org.ngelmakproject.repository.ChannelRepository;
import org.ngelmakproject.repository.SubscriptionRepository;
import org.ngelmakproject.security.UserService;
import org.ngelmakproject.security.UserService.UserPrincipal;
import org.ngelmakproject.web.rest.dto.ChannelDTO;
import org.ngelmakproject.web.rest.dto.SubscriptionDTO;
import org.ngelmakproject.web.rest.dto.SubscriptionStatsDTO;
import org.ngelmakproject.web.rest.errors.ChannelNotFoundException;
import org.ngelmakproject.web.rest.errors.UnauthorizedResourceAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Service Implementation for managing
 * {@link org.ngelmakproject.domain.Channel}.
 */
@Service
@Transactional
public class ChannelService {

    private static final Logger log = LoggerFactory.getLogger(ChannelService.class);
    private static final String ENTITY_NAME = "channel";

    private final ChannelRepository channelRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final FileService fileService;

    public ChannelService(ChannelRepository channelRepository, SubscriptionRepository subscriptionRepository,
            FileService fileService) {
        this.channelRepository = channelRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.fileService = fileService;
    }

    /**
     * Save a channel.
     *
     * @param channel the entity to save.
     * @return the persisted entity.
     */
    public Channel save(Channel channel) {
        log.info("Request to save Channel : {}", channel);

        /*
         * 1. Set the currently authenticated user as the owner of the channel to be
         * created.
         */
        // Retrieve ID of the user if authenticated or throught exception
        Long userId = UserService.getAuthenticatedUser().map(user -> user.id())
                .orElseThrow(() -> new UnauthorizedResourceAccessException(channel.getId(), ENTITY_NAME));
        channel.setUser(userId);
        /* 2. channel creation */
        String identifier = channel.getName().toLowerCase().trim()
                .replaceAll("[^a-z0-9]+", "-") // replace groups of non-alphanumerics
                .replaceAll("^-|-$", ""); // remove leading/trailing hyphens
        int counter = 1;
        String base = identifier;
        while (channelRepository.existsByIdentifier(identifier)) {
            identifier = base + "-" + counter++;
        }
        channel.setIdentifier(identifier);
        channel.setCreatedAt(Instant.now());
        return channelRepository.save(channel);
    }

    /**
     * Update a channel.
     *
     * @param channel the entity to save.
     * @return the persisted entity.
     */
    public Channel update(Channel channel) {
        log.debug("Request to update Channel : {}", channel);

        return findOneByCurrentUser().map(existingChannel -> {
            if (channel.getIdentifier() != null) {
                existingChannel.setIdentifier(channel.getIdentifier());
            }
            if (channel.getName() != null) {
                existingChannel.setName(channel.getName());
            }
            if (channel.getAvatar() != null) {
                existingChannel.setAvatar(channel.getAvatar());
            }
            if (channel.getBanner() != null) {
                existingChannel.setBanner(channel.getBanner());
            }
            if (channel.getCreatedAt() != null) {
                existingChannel.setCreatedAt(channel.getCreatedAt());
            }
            if (channel.getDescription() != null) {
                existingChannel.setDescription(channel.getDescription());
            }

            return existingChannel;
        }).map(channelRepository::save)
                .orElseThrow(ChannelNotFoundException::new);
    }

    /**
     * Get all the channels.
     *
     * @param pageable the pagination information.
     * @return the list of entities.
     */
    @Transactional(readOnly = true)
    public Page<ChannelDTO> findAll(Pageable pageable) {
        log.debug("Request to get all Channels");
        return channelRepository.findAll(pageable).map(ChannelDTO::from);
    }

    /**
     * Get one channel by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    @Transactional(readOnly = true)
    public Optional<ChannelDTO> findOne(Long id) {
        log.debug("Request to get Channel : {}", id);
        return channelRepository.findById(id).map(channel -> {
            var stats = getSubscriptionStatistics(channel.getId());
            return ChannelDTO.from(channel, stats);
        });
    }

    /**
     * Retrieves the Channel associated with the currently authenticated user.
     *
     * <p>
     * This method is designed to be safe even when invoked in contexts where
     * authentication is not guaranteed (e.g., unsecured endpoints). It performs
     * several defensive checks to avoid runtime exceptions such as
     * {@link ClassCastException} or {@link NullPointerException}.
     * </p>
     * 
     * [TODO] Save the channel if exists into cache.
     *
     * @return an {@code Optional<Channel>} for the authenticated user, or empty
     *         if
     *         no valid authenticated user is present.
     */
    @Transactional(readOnly = true)
    public Optional<ChannelDTO> findChannelDetails() {
        return UserService.getAuthenticatedUser().map(UserPrincipal::id)
                .flatMap(id -> channelRepository.findOneByUser(id).map(channel -> {
                    var stats = getSubscriptionStatistics(channel.getId());
                    return ChannelDTO.from(channel, stats);
                }));
    }

    /**
     * Retrieves the Channel associated with the currently authenticated user.
     *
     * <p>
     * This method is designed to be safe even when invoked in contexts where
     * authentication is not guaranteed (e.g., unsecured endpoints). It performs
     * several defensive checks to avoid runtime exceptions such as
     * {@link ClassCastException} or {@link NullPointerException}.
     * </p>
     *
     * @return an {@code Optional<Channel>} for the authenticated user, or empty
     *         if
     *         no valid authenticated user is present.
     */
    @Transactional(readOnly = true)
    public Optional<Channel> findOneByCurrentUser() {
        return UserService.getAuthenticatedUser().map(user -> {
            // [TODO] Save the channel if exists into cache.
            return channelRepository.findOneByUser(user.id());
        }).orElse(Optional.empty());
    }

    /**
     * Delete the channel by id.
     *
     * @param id the id of the entity.
     */
    public void delete(Long id) {
        log.debug("Request to delete Channel : {}", id);
        channelRepository.deleteById(id);
    }

    /**
     * Updates the avatar image of the current user's channel.
     *
     * <p>
     * The method uploads the provided media file, updates the channel's avatar URL,
     * and removes the previously stored avatar if one existed.
     * </p>
     *
     * @param media the new avatar file to upload
     * @return the updated {@link Channel}
     * @throws ChannelNotFoundException if the current user's channel cannot be
     *                                  found
     */
    public Channel updateAvatar(MultipartFile media) {
        return this.findOneByCurrentUser().map(
                channel -> {
                    log.info("Request to update Channel avatar : {}", channel);
                    String deletedAvatarUrl = channel.getAvatar();
                    var file = fileService.save(List.of(media)).get(0);
                    channel.setAvatar(file.getUrl());
                    channelRepository.save(channel);
                    if (deletedAvatarUrl != null && !deletedAvatarUrl.isEmpty()) {
                        fileService.deleteByUrls(List.of(deletedAvatarUrl));
                    }
                    log.debug("Changed Information for Channel: {}", channel);
                    return channel;
                }).orElseThrow(ChannelNotFoundException::new);
    }

    /**
     * Updates the banner image of the current user's channel.
     *
     * <p>
     * The method uploads the provided media file, updates the channel's banner URL,
     * and removes the previously stored banner if one existed.
     * </p>
     *
     * @param media the new banner file to upload
     * @return the updated {@link Channel}
     * @throws ChannelNotFoundException if the current user's channel cannot be
     *                                  found
     */
    public Channel updateBanner(MultipartFile media) {
        log.debug("Request to update Channel banner");
        return this.findOneByCurrentUser().map(
                channel -> {
                    String deletedBannerUrl = channel.getBanner();
                    var file = fileService.save(List.of(media)).get(0);
                    channel.setBanner(file.getUrl());
                    channelRepository.save(channel);
                    if (deletedBannerUrl != null && !deletedBannerUrl.isEmpty())
                        fileService.deleteByUrls(List.of(deletedBannerUrl));
                    log.debug("Changed information for Channel: {}", channel);
                    return channel;
                }).orElseThrow(ChannelNotFoundException::new);
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
        Channel subscriber = this.findOneByCurrentUser()
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
        Channel currentChannel = this.findOneByCurrentUser()
                .orElseThrow(ChannelNotFoundException::new);

        // Look for an existing subscription and delete it if present
        subscriptionRepository
                .findBySubscriberAndSubscribedTo(currentChannel.getId(), targetChannelId)
                .ifPresent(subscriptionRepository::delete);

        log.debug("Subscription removed for follower {} from channel {}", currentChannel.getId(),
                targetChannelId);
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
