package org.ngelmakproject.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.ngelmakproject.domain.Reaction;
import org.ngelmakproject.repository.ReactionRepository;
import org.ngelmakproject.web.rest.errors.ChannelNotFoundException;
import org.ngelmakproject.web.rest.errors.ResourceNotFoundException;
import org.ngelmakproject.web.rest.errors.UnauthorizedResourceAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service Implementation for managing
 * {@link org.ngelmakproject.domain.Reaction}.
 */
@Service
@Transactional
public class ReactionService {

    private static final Logger log = LoggerFactory.getLogger(ReactionService.class);

    private static final String ENTITY_NAME = "reaction";

    private final ReactionRepository reactionRepository;
    private final ChannelService channelService;

    ReactionService(ReactionRepository reactionRepository,
            ChannelService channelService) {
        this.reactionRepository = reactionRepository;
        this.channelService = channelService;
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
    @Transactional
    public Reaction save(Reaction reaction) {
        log.debug("Request to save Reaction : {}", reaction);
        return channelService.findOneByCurrentUser().map(channel -> {
            // [TODO] Only save on redis database and not on the persistent database for
            // fast response.
            reaction.setChannel(channel); // set the current connected user as owner of the reaction.
            return reactionRepository.save(reaction);
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
        log.debug("Request to update Reaction : {}", reaction);
        return channelService.findOneByCurrentUser().map(channel -> {
            return reactionRepository.findById(reaction.getId())
                    .map(existingPost -> {
                        if (channel.getId() != existingPost.getChannel().getId()) {
                            throw new UnauthorizedResourceAccessException(channel.getUser(), existingPost.getId(),
                                    ENTITY_NAME);
                        }
                        if (reaction.getEmoji() != null) {
                            existingPost.setEmoji(reaction.getEmoji());
                        }
                        // [TODO] Only save on redis database and not on the persistent database for
                        // fast response.
                        reactionRepository.save(existingPost);
                        return existingPost;
                    })
                    .orElseThrow(() -> new ResourceNotFoundException("Entity not found", ENTITY_NAME, "idnotfound"));
        }).orElseThrow(ChannelNotFoundException::new);
    }

    /**
     * Delete the reaction by id.
     *
     * @param id the id of the entity.
     */
    public void delete(Long id) {
        log.debug("Request to delete Post : {}", id);

        // [TODO] Only save on redis database and not on the persistent database for
        // fast response.
        reactionRepository.deleteById(id);
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
