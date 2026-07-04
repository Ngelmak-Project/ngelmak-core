package org.ngelmakproject.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.ngelmakproject.domain.Reaction;
import org.ngelmakproject.repository.ReactionRepository;
import org.ngelmakproject.service.cache.ReactionRedisService;
import org.ngelmakproject.web.rest.errors.ChannelNotFoundException;
import org.ngelmakproject.web.rest.errors.ResourceNotFoundException;
import org.ngelmakproject.web.rest.errors.UnauthorizedResourceAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service Implementation for managing
 * {@link org.ngelmakproject.domain.Reaction}.
 */
@Service
public class ReactionService {

    private static final Logger log = LoggerFactory.getLogger(ReactionService.class);

    private static final String ENTITY_NAME = "reaction";

    private final ReactionRepository reactionRepository;
    private final ChannelService channelService;
    private final ReactionRedisService reactionRedisService;

    ReactionService(ReactionRepository reactionRepository,
            ChannelService channelService,
            ReactionRedisService reactionRedisService) {
        this.reactionRepository = reactionRepository;
        this.channelService = channelService;
        this.reactionRedisService = reactionRedisService;
    }

    /**
     * Save a reaction.
     * 
     * <p>
     * This method will save post in readis database for fast response. Later all
     * gathered Reaction entities will be flushed to the database.
     * </p>
     *
     * @param reaction the entity to save.
     * @return the persisted entity.
     */
    public Reaction save(Reaction reaction) {
        log.debug("Request to save Reaction : {}", reaction);
        return channelService.findOneByCurrentUser().map(channel -> {
            reaction.setChannel(channel);
            // Save to Redis
            reactionRedisService.queueCreate(reaction);
            return reaction; // return immediately
        }).orElseThrow(ChannelNotFoundException::new);
    }

    /**
     * Update a reaction.
     * This function can eventually delete some files through the given
     * deletedFiles variable.
     *
     * @param reaction the entity to save.
     * @return the persisted entity.
     */
    public Reaction update(Reaction reaction) {
        log.debug("Queue Reaction UPDATE : {}", reaction);

        return channelService.findOneByCurrentUser().map(channel -> {
            Reaction existing = reactionRepository.findById(reaction.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Entity not found", ENTITY_NAME, "idnotfound"));

            if (!existing.getChannel().getId().equals(channel.getId())) {
                throw new UnauthorizedResourceAccessException(channel.getUser(), existing.getId(), ENTITY_NAME);
            }

            existing.setEmoji(reaction.getEmoji());

            // Save to Redis
            reactionRedisService.queueUpdate(reaction);

            return existing;
        }).orElseThrow(ChannelNotFoundException::new);
    }

    /**
     * Delete the reaction by id.
     *
     * @param id the id of the entity.
     */
    public void delete(Long id) {
        log.debug("Queue Reaction DELETE : {}", id);

        var channel = channelService.findOneByCurrentUser()
                .orElseThrow(ChannelNotFoundException::new);

        reactionRedisService.queueDelete(id, channel);
        log.debug("Queued DELETE operation for reaction {}", id);
    }

    /**
     * Groups reactions by post ID.
     *
     * @param reactions flat list of reactions for many posts
     * @return map: postId → list of reactions
     */
    public static Map<Long, List<Reaction>> groupReactionsByPost(List<Reaction> reactions) {
        Map<Long, List<Reaction>> map = new HashMap<>();

        for (Reaction reaction : reactions) {
            Long postId = reaction.getPost().getId();
            map.computeIfAbsent(postId, id -> new ArrayList<>()).add(reaction);
        }

        return map;
    }
}
