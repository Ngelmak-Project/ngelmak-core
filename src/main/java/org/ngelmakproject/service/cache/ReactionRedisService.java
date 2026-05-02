package org.ngelmakproject.service.cache;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.ngelmakproject.domain.Channel;
import org.ngelmakproject.domain.Reaction;
import org.ngelmakproject.repository.ReactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.transaction.Transactional;

@Service
public class ReactionRedisService {
    private static final Logger log = LoggerFactory.getLogger(ReactionRedisService.class);

    private static final String REDIS_CREATE_KEY = "reaction:create";
    private static final String REDIS_UPDATE_KEY = "reaction:update";
    private static final String REDIS_DELETE_KEY = "reaction:delete";

    private final ReactionRepository reactionRepository;
    private final RedisTemplate<String, String> redis;

    public ReactionRedisService(ReactionRepository reactionRepository, RedisTemplate<String, String> redis) {
        this.reactionRepository = reactionRepository;
        this.redis = redis;
    }

    /**
     * Enqueues a new Reaction for async creation and assigns it a generated ID.
     *
     * @param reaction the Reaction entity to enqueue for creation
     */
    public void queueCreate(Reaction reaction) {
        Long uuid = CacheTools.generateUUID();
        reaction.setId(uuid);
        String value = CacheTools.toJson(reaction);
        redis.opsForHash().put(
                REDIS_CREATE_KEY,
                uuid.toString(),
                value);
        log.info("📦 Redis | Reaction saved - {}", value);
    }

    /**
     * Enqueues a Reaction update, replacing any pending create entry for the same
     * ID.
     *
     * @param reaction the Reaction entity to enqueue for update
     */
    public void queueUpdate(Reaction reaction) {
        // Check if hashKey already exist.
        String hashKey = reaction.getId().toString();
        if (redis.opsForHash().hasKey(REDIS_CREATE_KEY, hashKey)) {
            redis.opsForHash().delete(REDIS_CREATE_KEY, hashKey);
        }
        String value = CacheTools.toJson(reaction);
        redis.opsForHash().put(REDIS_UPDATE_KEY, hashKey, value);
        log.info("📦 Redis | Reaction updated - {}", value);
    }

    /**
     * Enqueues a Reaction ID for async deletion.
     *
     * @param id the ID of the Reaction to delete
     */
    public void queueDelete(Long id, Channel channel) {
        Reaction deletedReaction = new Reaction();
        deletedReaction.setId(id);
        deletedReaction.setChannel(channel);
        redis.opsForHash().put(REDIS_DELETE_KEY, id.toString(), CacheTools.toJson(deletedReaction));
        log.warn("📦 Redis | Reaction deleted - {}", id);
    }

    /**
     * Flushes pending CREATE and UPDATE reaction operations from Redis to the
     * database.
     * Runs every 2 seconds and merges updates with existing reactions.
     */
    @Transactional
    @Scheduled(fixedDelay = 2, timeUnit = TimeUnit.SECONDS)
    public void flushPendingReactions() {
        List<Reaction> toSave = new ArrayList<>();
        Set<Object> createdKeys = redis.opsForHash().keys(REDIS_CREATE_KEY);
        for (Object key : createdKeys) {
            String json = (String) redis.opsForHash().get(REDIS_CREATE_KEY, key);
            Reaction newReaction = CacheTools.fromJson(json, Reaction.class);
            newReaction.setId(null);
            toSave.add(newReaction);
            log.info("Saved {} reaction(s)", createdKeys.size());
        }
        Set<Object> updatedKeys = redis.opsForHash().keys(REDIS_UPDATE_KEY);
        for (Object key : updatedKeys) {
            String json = (String) redis.opsForHash().get(REDIS_UPDATE_KEY, key);
            toSave.add(CacheTools.fromJson(json, Reaction.class));
            log.info("Updated {} reaction(s)", updatedKeys.size());
        }
        if (toSave.isEmpty()) {
            return;
        }
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

        redis.opsForHash().delete(REDIS_CREATE_KEY, createdKeys.toArray());
        redis.opsForHash().delete(REDIS_UPDATE_KEY, updatedKeys.toArray());
        log.info("Flushing {} pending reaction operations", toSave.size());
    }

    /**
     * Flushes pending DELETE reaction operations from Redis to the database.
     * Runs every 2 seconds and removes all queued reactions in batch.
     */
    @Transactional
    @Scheduled(fixedDelay = 2, timeUnit = TimeUnit.SECONDS)
    public void flushDeleteQueue() {
        Set<Object> processedKeys = redis.opsForHash().keys(REDIS_DELETE_KEY);
        if (processedKeys.isEmpty()) {
            return;
        }

        Set<Long> toDeleteIds = new HashSet<>();
        Set<Long> toDeleteChannelIds = new HashSet<>();
        for (Object key : processedKeys) {
            Reaction reaction = CacheTools.fromJson((String) redis.opsForHash().get(REDIS_DELETE_KEY, key),
                    Reaction.class);
            toDeleteIds.add(reaction.getId());
            toDeleteChannelIds.add(reaction.getChannel().getId());
        }

        reactionRepository.deleteByIdInAndChannelIn(
                toDeleteIds,
                toDeleteChannelIds);
        redis.opsForHash().delete(REDIS_DELETE_KEY, processedKeys.toArray());
        log.info("Removed {} processed operations from Redis", processedKeys.size());
    }
}
