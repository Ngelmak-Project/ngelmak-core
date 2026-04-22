package org.ngelmakproject.service;

import java.text.Normalizer;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import org.ngelmakproject.domain.Channel;
import org.ngelmakproject.domain.Subscription;
import org.ngelmakproject.repository.ChannelRepository;
import org.ngelmakproject.repository.SubscriptionRepository;
import org.ngelmakproject.repository.projection.ActiveChannelProjection;
import org.ngelmakproject.security.UserService;
import org.ngelmakproject.security.UserService.UserPrincipal;
import org.ngelmakproject.web.rest.dto.ActiveChannel;
import org.ngelmakproject.web.rest.dto.ChannelDTO;
import org.ngelmakproject.web.rest.dto.SubscriptionDTO;
import org.ngelmakproject.web.rest.dto.SubscriptionStatsDTO;
import org.ngelmakproject.web.rest.errors.ChannelNotFoundException;
import org.ngelmakproject.web.rest.errors.UnauthorizedResourceAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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
     * Creates a new channel for the authenticated user.
     *
     * <p>
     * The method:
     * </p>
     * <ul>
     * <li>assigns the current user as the owner</li>
     * <li>generates a unique identifier based on the channel name</li>
     * <li>sets creation metadata</li>
     * <li>persists the channel</li>
     * </ul>
     *
     * @param channel the channel to create
     * @return the persisted channel
     */
    public Channel save(Channel channel) {
        log.info("Request to save Channel : {}", channel);

        // Assign owner
        Long userId = UserService.getAuthenticatedUser()
                .map(user -> user.id())
                .orElseThrow(() -> new UnauthorizedResourceAccessException(channel.getId(), ENTITY_NAME));
        channel.setUser(userId);

        // Generate identifier + metadata
        channel.setIdentifier(generateUniqueIdentifier(channel.getName()));
        channel.setCreatedAt(Instant.now());

        return channelRepository.save(channel);
    }

    /**
     * Updates the current user's channel.
     *
     * <p>
     * When the channel name changes, a new unique identifier is generated.
     * Other fields are updated only if provided.
     * </p>
     *
     * @param channel the updated fields
     * @return the persisted channel
     */
    public Channel update(Channel channel) {
        log.debug("Request to update Channel : {}", channel);
        return findOneByCurrentUser()
                .map(existingChannel -> {
                    // Name changed → regenerate identifier
                    if (channel.getName() != null &&
                            !channel.getName().equals(existingChannel.getName())) {
                        existingChannel.setName(channel.getName());
                        existingChannel.setIdentifier(generateUniqueIdentifier(channel.getName()));
                    }
                    if (channel.getAvatar() != null)
                        existingChannel.setAvatar(channel.getAvatar());
                    if (channel.getBanner() != null)
                        existingChannel.setBanner(channel.getBanner());
                    if (channel.getCreatedAt() != null)
                        existingChannel.setCreatedAt(channel.getCreatedAt());
                    if (channel.getDescription() != null)
                        existingChannel.setDescription(channel.getDescription());
                    return existingChannel;
                })
                .map(channelRepository::save)
                .orElseThrow(ChannelNotFoundException::new);
    }

    /**
     * Generates a clean, URL‑friendly, SEO‑friendly, and unique identifier (slug)
     * for a channel based on its name.
     *
     * <p>
     * The method performs:
     * </p>
     * <ul>
     * <li>Unicode normalization (é → e, ö → o, etc.)</li>
     * <li>Emoji and symbol removal</li>
     * <li>Lowercasing and trimming</li>
     * <li>Replacing non‑alphanumeric groups with hyphens</li>
     * <li>Removing duplicate or trailing hyphens</li>
     * <li>Ensuring uniqueness by appending "-1", "-2", ...</li>
     * </ul>
     *
     * <h4>Example</h4>
     * 
     * <pre>
     * Input:  "  🎉 My Amazing Chánnel!!!  "
     * Output: "my-amazing-channel"
     *
     * If "my-amazing-channel" already exists:
     * Output: "my-amazing-channel-1"
     * </pre>
     *
     * @param name the channel name to convert into a unique identifier
     * @return a unique, normalized slug
     */
    public String generateUniqueIdentifier(String name) {
        // Normalize accents (é → e, ç → c, ü → u)
        String normalized = Normalizer.normalize(name, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", ""); // remove diacritics
        // Remove emojis and symbols
        normalized = normalized.replaceAll("[^\\p{Alnum}\\s-]", "");
        // Lowercase, trim, replace non-alphanumeric groups with hyphens
        String identifier = normalized.toLowerCase().trim()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", ""); // remove leading/trailing hyphens
        // Fallback if everything was removed
        if (identifier.isBlank()) {
            identifier = "channel";
        }
        // Ensure uniqueness
        String base = identifier;
        int counter = 1;
        while (channelRepository.existsByIdentifier(identifier)) {
            identifier = base + "-" + counter++;
        }
        return identifier;
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
     * Get one channel by identifier.
     *
     * @param identifier the identifier of the entity.
     * @return the entity.
     */
    @Transactional(readOnly = true)
    public Optional<ChannelDTO> findOneByIdentifier(String identifier) {
        log.debug("Request to get Channel : {}", identifier);
        return channelRepository.findOneByIdentifier(identifier).map(channel -> {
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
     * If the subscription does not exist or does not bellong to the connected user,
     * then no action.
     *
     * @param id the ID of the subscription to remove
     * @throws ChannelNotFoundException if the current user's channel cannot be
     *                                  found
     */
    public void unfollowUser(Long id) {
        log.debug("Request to unfollow Channel : {}", id);
        // Load the channel associated with the current authenticated user
        Channel currentChannel = this.findOneByCurrentUser()
                .orElseThrow(ChannelNotFoundException::new);

        // Look for an existing subscription and delete it if present
        subscriptionRepository
                .findById(id).filter(s -> s.getSubscriber().getId().equals(currentChannel.getId()))
                .ifPresent(subscriptionRepository::delete);
    }

    /**
     * Retrieves the top 10 most active channels based on post activity in the last
     * 7 days.
     * 
     * Fetches channel data from the repository and maps projections to
     * ActiveChannel DTOs
     * for API response serialization.
     * 
     * Channels are ranked by:
     * 1. Post count in the last 7 days (descending)
     * 2. Channel creation date (ascending, as tiebreaker)
     * 
     * @return List of ActiveChannel DTOs containing channel metadata and post
     *         count.
     *         Returns an empty list if no channels exist.
     */
    public List<ActiveChannel> getActiveChannels() {
        log.debug("Fetching most active channels (7-day window)");
        return this.channelRepository.topActiveChannels(Instant.now().minus(7, ChronoUnit.DAYS), PageRequest.of(0, 10))
                .stream()
                .map(e -> new ActiveChannel(
                        e.getId(),
                        e.getName(),
                        e.getIdentifier(),
                        e.getAvatar(),
                        e.getBanner(),
                        e.getDescription(),
                        e.getPostCount()))
                .toList();
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
