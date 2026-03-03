package org.ngelmakproject.web.rest;

import java.net.URISyntaxException;

import org.ngelmakproject.domain.Channel;
import org.ngelmakproject.domain.Subscription;
import org.ngelmakproject.service.ChannelService;
import org.ngelmakproject.web.rest.dto.ChannelDTO;
import org.ngelmakproject.web.rest.dto.PageDTO;
import org.ngelmakproject.web.rest.dto.SubscriptionDTO;
import org.ngelmakproject.web.rest.errors.BadRequestAlertException;
import org.ngelmakproject.web.rest.util.HeaderUtil;
import org.ngelmakproject.web.rest.util.ResponseUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;

/**
 * REST controller for managing
 * {@link org.ngelmakproject.domain.Channel}.
 */
@RestController
@RequestMapping("/api/channels")
public class ChannelResource {

    @ResponseStatus(HttpStatus.NOT_FOUND) // Or @ResponseStatus(HttpStatus.NO_CONTENT)
    private static class ChannelResourceException extends RuntimeException {
        private ChannelResourceException(String message) {
            super(message);
        }
    }

    private static final Logger log = LoggerFactory.getLogger(ChannelResource.class);

    private static final String ENTITY_NAME = "channel";

    @Value("${spring.application.name}")
    private String applicationName;

    private final ChannelService channelService;

    public ChannelResource(ChannelService channelService) {
        this.channelService = channelService;
    }

    /**
     * {@code POST  /channels} : Create a new channel.
     *
     * @param channel the channel to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with
     *         body the new channel, or with status {@code 400 (Bad Request)} if
     *         the channel has already an ID.
     */
    @PostMapping("")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ChannelDTO> createChannel(Authentication authentication,
            @Valid @RequestBody Channel channel) {
        log.debug("REST request to save Channel : {}", channel);
        if (channel.getId() != null) {
            throw new BadRequestAlertException("A new channel cannot already have an ID", ENTITY_NAME, "idexists");
        }
        var newChannel = channelService.save(channel);
        return ResponseEntity.ok()
                .headers(HeaderUtil.createEntityCreationAlert(applicationName, ENTITY_NAME,
                        newChannel.getId().toString()))
                .body(ChannelDTO.from(newChannel));
    }

    /**
     * {@code PUT  /channels/:id} : Updates an existing channel.
     *
     * @param id      the id of the channel to save.
     * @param channel the channel to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body
     *         the updated channel,
     *         or with status {@code 400 (Bad Request)} if the channel is not
     *         valid,
     *         or with status {@code 500 (Internal Server Error)} if the channel
     *         couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PutMapping("")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ChannelDTO> updateChannel(@Valid @RequestBody Channel channel) {
        log.debug("REST request to update Channel : {}", channel);
        if (channel.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        var newChannel = channelService.update(channel);
        return ResponseEntity.ok()
                .headers(HeaderUtil.createEntityUpdateAlert(applicationName, ENTITY_NAME,
                        newChannel.getId().toString()))
                .body(ChannelDTO.from(newChannel));
    }

    /**
     * {@code GET  /channels} : get all the channels.
     *
     * @param pageable the pagination information.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list
     *         of channels in body.
     */
    @GetMapping("")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<PageDTO<ChannelDTO>> getAllChannels(Pageable pageable) {
        log.debug("REST request to get a page of Channels");
        return ResponseEntity.ok().body(PageDTO.from(channelService.findAll(pageable)));
    }

    /**
     * {@code GET  /channels/me} : get the connected user channel.
     *
     * @param id the id of the channel to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body
     *         the channel, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ChannelDTO> personalChannel(Authentication authentication) {
        log.debug("REST request to get connected Channel");
        return ResponseUtil.wrapOrNotFound(channelService.findChannelDetails());
    }

    /**
     * {@code GET  /channels/:id} : get the "id" channel.
     *
     * @param id the id of the channel to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body
     *         the channel, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ChannelDTO> getChannel(@PathVariable Long id) {
        log.debug("REST request to get Channel : {}", id);
        return ResponseUtil.wrapOrNotFound(channelService.findOne(id));
    }

    /**
     * {@code DELETE  /channels/:id} : delete the "id" channel.
     *
     * @param id the id of the channel to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Void> blockChannel(@PathVariable Long id) {
        log.debug("REST request to delete Channel : {}", id);
        channelService.delete(id);
        return ResponseEntity.noContent()
                .headers(HeaderUtil.createEntityDeletionAlert(applicationName, ENTITY_NAME, id.toString()))
                .build();
    }

    /**
     * {@code DELETE  /channels/:id} : delete the "id" channel.
     *
     * @param id the id of the channel to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Void> deleteChannel(@PathVariable Long id) {
        log.debug("REST request to delete Channel : {}", id);
        channelService.delete(id);
        return ResponseEntity.noContent()
                .headers(HeaderUtil.createEntityDeletionAlert(applicationName, ENTITY_NAME, id.toString()))
                .build();
    }

    /**
     * {@code PUT   /channel/upload-avatar} : Upload an avatar image for the current
     * user channel.
     * 
     * @param file
     * @return the current user.
     */
    @PutMapping("/upload-avatar")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ChannelDTO> updateAvatar(@RequestParam MultipartFile file) {
        log.debug("REST request to upload the user's channel avatar");
        var updatedChannel = channelService.updateAvatar(file);
        return ResponseEntity.ok().body(ChannelDTO.from(updatedChannel));
    }

    /**
     * {@code PUT   /channel/upload-banner} : Upload an banner image for the current
     * user channel.
     * 
     * @param file
     * @return the current user.
     */
    @PutMapping("/upload-banner")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ChannelDTO> updateBanner(@RequestParam("file") MultipartFile file) {
        log.debug("REST request to upload the user's channel banner");
        var updatedChannel = channelService.updateBanner(file);
        return ResponseEntity.ok().body(ChannelDTO.from(updatedChannel));
    }

    /**
     * {@code POST /channel/follow} : Follow the target
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
    @PostMapping("/follow")
    public ResponseEntity<SubscriptionDTO> follow(@RequestBody Channel channel) {
        log.debug("REST request to follow Channel : {}", channel);
        Subscription subscription = channelService.followChannel(channel);
        return ResponseEntity.ok(SubscriptionDTO.from(subscription));
    }

    /**
     * {@code DELETE /channel/unfollow/:id} : Unfollow the target
     * channel.
     *
     * <p>
     * If no subscription exists, the operation is a no-op.
     * The method is idempotent.
     * </p>
     *
     * @param id the ID of the subscription to remove/unfollow
     * @return {@code 204 No Content}
     */
    @DeleteMapping("/unfollow/{id}")
    public ResponseEntity<Void> unfollow(@PathVariable Long id) {
        log.debug("REST request to unfollow Channel : {}", id);
        channelService.unfollowUser(id);
        return ResponseEntity.noContent().build();
    }
}
