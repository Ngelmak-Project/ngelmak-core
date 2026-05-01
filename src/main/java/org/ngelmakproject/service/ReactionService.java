package org.ngelmakproject.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.ngelmakproject.domain.Reaction;
import org.ngelmakproject.repository.ReactionRepository;
import org.ngelmakproject.service.operation.Operation;
import org.ngelmakproject.service.operation.Operation.OperationType;
import org.ngelmakproject.web.rest.errors.ChannelNotFoundException;
import org.ngelmakproject.web.rest.errors.ResourceNotFoundException;
import org.ngelmakproject.web.rest.errors.UnauthorizedResourceAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service Implementation for managing
 * {@link org.ngelmakproject.domain.Reaction}.
 */
@Service
public class ReactionService {

    private static final Logger log = LoggerFactory.getLogger(ReactionService.class);

    private static final String ENTITY_NAME = "reaction";
    private static final String REDIS_PENDING_KEY = "reaction:pending";

    private final ReactionRepository reactionRepository;
    private final ChannelService channelService;
    private final RedisTemplate<String, String> redisTemplate;

    ReactionService(ReactionRepository reactionRepository,
            ChannelService channelService,
            RedisTemplate<String, String> redisTemplate) {
        this.reactionRepository = reactionRepository;
        this.channelService = channelService;
        this.redisTemplate = redisTemplate;
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
            Operation<Reaction> op = Operation.createOperation(reaction);
            reaction.setId(op.idAsLong()); // Set post ID from operation
            redisTemplate.opsForHash()
                    .put(REDIS_PENDING_KEY, op.id(), op.toJson());
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

            // If the key is found then remove.
            removeRedisById(reaction.getId());

            existing.setEmoji(reaction.getEmoji());

            // Save to Redis
            Operation<Reaction> op = Operation.updateOperation(existing.getId(), existing);
            redisTemplate.opsForHash()
                    .put(REDIS_PENDING_KEY, op.id(), op.toJson());

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

        // Check Redis for pending operations. Try to find a pending CREATE with
        // matching reaction ID
        if (removeRedisById(id)) {
            return;
        }

        // No pending CREATE found → queue a DELETE operation
        Reaction reaction = new Reaction();
        reaction.setId(id);
        Operation<Reaction> deleteOp = new Operation<>(
                Instant.now().getEpochSecond(),
                OperationType.DELETE,
                reaction);

        redisTemplate.opsForHash()
                .put(REDIS_PENDING_KEY, deleteOp.id(), deleteOp.toJson());

        log.info("Queued DELETE operation for reaction {}", id);
    }

    private boolean removeRedisById(long id) {
        // If the key is found then remove.
        Map<Object, Object> pendingOps = redisTemplate.opsForHash().entries(REDIS_PENDING_KEY);
        for (Map.Entry<Object, Object> entry : pendingOps.entrySet()) {
            Operation<Reaction> op = Operation.fromJson((String) entry.getValue());
            if (op.idAsLong() == id) {
                redisTemplate.opsForHash().delete(REDIS_PENDING_KEY, entry.getKey());
                log.warn("Cancelled pending CREATE/UDATE for reaction {}", op.id());
                return true;
            }
        }
        return false;
    }

    /**
     * Flushes pending reaction operations from Redis to the database.
     * Processes CREATE, UPDATE, and DELETE operations in batches.
     * Scheduled every 2 minutes.
     */
    @Transactional
    @Scheduled(fixedDelay = 2, timeUnit = TimeUnit.MINUTES)
    public void flushPendingReactions() {
        Map<Object, Object> entries = redisTemplate.opsForHash()
                .entries(REDIS_PENDING_KEY);

        if (entries.isEmpty()) {
            return;
        }

        log.info("Flushing {} pending reaction operations", entries.size());

        List<Reaction> toSave = new ArrayList<>();
        List<Long> toDelete = new ArrayList<>();
        List<Object> processedKeys = new ArrayList<>();

        for (Map.Entry<Object, Object> entry : entries.entrySet()) {
            Object key = entry.getKey();
            String json = (String) entry.getValue();
            Operation<Reaction> op = Operation.fromJson(json);
            switch (op.type()) {
                case CREATE -> {
                    op.data().setId(null); // Clear ID for new comments
                    toSave.add(op.data());
                    processedKeys.add(key);
                }
                case UPDATE -> {
                    toSave.add(op.data());
                    processedKeys.add(key);
                }
                case DELETE -> {
                    toDelete.add(op.data().getId());
                    processedKeys.add(key);
                }
                default -> {
                }
            }
        }

        if (!toSave.isEmpty()) {
            // Fetch existing reactions by post and channel IDs
            Map<String, Reaction> existing = reactionRepository
                    .findByPostInAndChannelIn(
                            toSave.stream().map(r -> r.getPost().getId()).collect(Collectors.toList()),
                            toSave.stream().map(r -> r.getChannel().getId()).collect(Collectors.toList()))
                    .stream()
                    .collect(Collectors.toMap(r -> r.getPost().getId() + ":" + r.getChannel().getId(),
                            r -> r));
            // Merge: update existing reactions or keep new ones
            toSave.replaceAll(e -> {
                Reaction ex = existing.get(e.getPost().getId() + ":" + e.getChannel().getId());
                if (ex != null) {
                    ex.setEmoji(e.getEmoji());
                    return ex;
                }
                return e;
            });
            // Save or update
            reactionRepository.saveAll(toSave);
            log.info("Saved/updated {} reactions", toSave.size());
        }

        if (!toDelete.isEmpty()) {
            reactionRepository.deleteAllById(toDelete);
            log.info("Deleted {} reactions", toDelete.size());
        }

        if (!processedKeys.isEmpty()) {
            redisTemplate.opsForHash().delete(REDIS_PENDING_KEY, processedKeys.toArray());
            log.info("Removed {} processed operations from Redis", processedKeys.size());
        }
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
