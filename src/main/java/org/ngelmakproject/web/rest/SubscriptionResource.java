package org.ngelmakproject.web.rest;

import org.ngelmakproject.domain.Channel;
import org.ngelmakproject.domain.Subscription;
import org.ngelmakproject.service.SubscriptionService;
import org.ngelmakproject.web.rest.dto.SubscriptionStatsDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for managing {@link org.ngelmakproject.domain.Subscription}.
 */
@RestController
@RequestMapping("/api/subscriptions")
public class SubscriptionResource {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionResource.class);

    @Value("${spring.application.name}")
    private String applicationName;

    private final SubscriptionService subscriptionService;

    public SubscriptionResource(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    /**
     * {@code POST /subscriptions/follow/:targetChannelId} : Follow the target
     * channel.
     *
     * <p>
     * If the subscription already exists, it is returned as-is.
     * Otherwise, a new subscription is created.
     * </p>
     *
     * @param targetChannelId the ID of the channel to follow
     * @return the existing or newly created Subscription
     */
    @PostMapping("/follow/{targetChannelId}")
    public ResponseEntity<Subscription> follow(@RequestBody Channel channel) {
        log.debug("REST request to follow Channel : {}", channel);
        Subscription subscription = subscriptionService.followChannel(channel);
        return ResponseEntity.ok(subscription);
    }

    /**
     * {@code DELETE /subscriptions/unfollow/:targetChannelId} : Unfollow the target
     * channel.
     *
     * <p>
     * If no subscription exists, the operation is a no-op.
     * The method is idempotent.
     * </p>
     *
     * @param targetChannelId the ID of the channel to unfollow
     * @return {@code 204 No Content}
     */
    @DeleteMapping("/unfollow/{targetChannelId}")
    public ResponseEntity<Void> unfollow(@PathVariable Long targetChannelId) {
        log.debug("REST request to unfollow Channel : {}", targetChannelId);
        subscriptionService.unfollowUser(targetChannelId);
        return ResponseEntity.noContent().build();
    }

    /**
     * {@code GET /subscriptions/stats/:channelId} : Retrieve subscription
     * statistics for a channel.
     *
     * <p>
     * Returns all subscription relations involving the given channel,
     * either as subscriber or as subscribed-to, organized into a structured DTO.
     * </p>
     *
     * @param channelId the ID of the channel to analyze
     * @return a DTO containing followers, following, and raw subscription data
     */
    @GetMapping("/stats/{channelId}")
    public ResponseEntity<SubscriptionStatsDTO> getStats(@PathVariable Long channelId) {
        log.debug("REST request to get subscription statistics for Channel : {}", channelId);
        SubscriptionStatsDTO dto = subscriptionService.getSubscriptionStatistics(channelId);
        return ResponseEntity.ok(dto);
    }
}
