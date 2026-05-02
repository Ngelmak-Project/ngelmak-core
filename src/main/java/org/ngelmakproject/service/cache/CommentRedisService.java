package org.ngelmakproject.service.cache;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.ngelmakproject.domain.Comment;
import org.ngelmakproject.repository.CommentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommentRedisService {
    private static final Logger log = LoggerFactory.getLogger(CommentRedisService.class);

    private static final String REDIS_CREATE_KEY = "comment:create";
    private static final String REDIS_UPDATE_KEY = "comment:update";
    private static final String REDIS_DELETE_KEY = "comment:delete";
    private static final String REDIS_REPLY_COUNT_KEY = "comment:replycount";

    private record ReplyCountDTO(long id, int count) {
    }

    private final CommentRepository commentRepository;
    private final RedisTemplate<String, String> redis;

    public CommentRedisService(CommentRepository commentRepository, RedisTemplate<String, String> redis) {
        this.commentRepository = commentRepository;
        this.redis = redis;
    }

    /**
     * Enqueues a new Comment for async creation and assigns it a generated ID.
     *
     * @param comment the Comment entity to enqueue for creation
     */
    public void queueCreate(Comment comment) {
        Long uuid = CacheTools.generateUUID();
        comment.setId(uuid);
        String value = CacheTools.toJson(comment);
        redis.opsForHash().put(
                REDIS_CREATE_KEY,
                uuid.toString(),
                value);
        log.info("📦 Redis | Comment saved - {}", value);
    }

    /**
     * Enqueues a Comment update, replacing any pending create entry for the same
     * ID.
     *
     * @param comment the Comment entity to enqueue for update
     */
    public void queueUpdate(Comment comment) {
        // Check if hashKey already exist.
        String hashKey = comment.getId().toString();
        if (redis.opsForHash().hasKey(REDIS_CREATE_KEY, hashKey)) {
            redis.opsForHash().delete(REDIS_CREATE_KEY, hashKey);
        }
        String value = CacheTools.toJson(comment);
        redis.opsForHash().put(REDIS_UPDATE_KEY, hashKey, value);
        log.info("📦 Redis | Comment updated - {}", value);
    }

    /**
     * Enqueues a Comment ID for async deletion.
     *
     * @param id the ID of the Comment to delete
     */
    public void queueDelete(Long id) {
        redis.opsForHash().put(REDIS_DELETE_KEY, id.toString(), id);
        log.warn("📦 Redis | Comment deleted - {}", id);
    }

    /**
     * Enqueues a comment count change for a Comment.
     *
     * @param commentId the ID of the Comment whose comment count is updated
     * @param count     the delta to apply (positive or negative)
     */
    public void queueReplyCount(long commentId, int count) {
        // Record to redis for updating reply count.
        String json = CacheTools.toJson(new ReplyCountDTO(commentId, count));
        redis.opsForHash()
                .put(REDIS_REPLY_COUNT_KEY, commentId, json);
        log.info("📦 Redis | Comment comment count - {} → {}", commentId, count);
    }

    /**
     * Flushes pending CREATE and UPDATE comment operations from Redis to the
     * database.
     * Runs every 2 seconds and persists all queued comments in batch.
     */
    @Transactional
    @Scheduled(fixedDelay = 2, timeUnit = TimeUnit.SECONDS)
    public void flushPendingComments() {
        List<Comment> toSave = new ArrayList<>();
        Set<Object> createdKeys = redis.opsForHash().keys(REDIS_CREATE_KEY);
        for (Object key : createdKeys) {
            String json = (String) redis.opsForHash().get(REDIS_CREATE_KEY, key);
            Comment newComment = CacheTools.fromJson(json, Comment.class);
            newComment.setId(null);
            toSave.add(newComment);
            log.info("Saved {} comment(s)", createdKeys.size());
        }
        Set<Object> updatedKeys = redis.opsForHash().keys(REDIS_UPDATE_KEY);
        for (Object key : updatedKeys) {
            String json = (String) redis.opsForHash().get(REDIS_UPDATE_KEY, key);
            toSave.add(CacheTools.fromJson(json, Comment.class));
            log.info("Updated {} comment(s)", updatedKeys.size());
        }
        if (toSave.isEmpty()) {
            return;
        }

        commentRepository.saveAll(toSave);
        redis.opsForHash().delete(REDIS_CREATE_KEY, createdKeys.toArray());
        redis.opsForHash().delete(REDIS_UPDATE_KEY, updatedKeys.toArray());
        log.info("Flushing {} pending comment operations", toSave.size());
    }

    /**
     * Flushes pending DELETE comment operations from Redis to the database.
     * Runs every 2 seconds and removes all queued comment IDs in batch.
     */
    @Transactional
    @Scheduled(fixedDelay = 2, timeUnit = TimeUnit.SECONDS)
    public void flushDeleteQueue() {
        Set<Object> processedKeys = redis.opsForHash().keys(REDIS_DELETE_KEY);
        if (processedKeys.isEmpty()) {
            return;
        }
        Set<Long> toDelete = new HashSet<>();
        for (Object key : processedKeys) {
            Long id = Long.valueOf((String) redis.opsForHash().get(REDIS_DELETE_KEY, key));
            toDelete.add(id);
        }
        commentRepository.deleteAllById(toDelete);
        redis.opsForHash().delete(REDIS_DELETE_KEY, processedKeys.toArray());
        log.info("Removed {} processed operations from Redis", processedKeys.size());
    }

    /**
     * Periodically flushes aggregated reply count updates from Redis to the
     * database.
     * 
     * <p>
     * Runs every 2 minutes, retrieving pending operations from Redis, aggregating
     * count changes by comment ID, and applying batch updates to the database.
     * </p>
     */
    @Transactional
    @Scheduled(fixedDelay = 2, timeUnit = TimeUnit.MINUTES)
    public void flushReplyCount() {
        Set<Object> processedKeys = redis.opsForHash().keys(REDIS_REPLY_COUNT_KEY);

        if (processedKeys.isEmpty()) {
            return;
        }

        log.info("Flushing {} pending reply count operations", processedKeys.size());

        // Aggregate and apply updates in one operation
        processedKeys.stream()
                .map(k -> (String) redis.opsForHash().get(REDIS_REPLY_COUNT_KEY, k))
                .map(json -> CacheTools.fromJson(json, ReplyCountDTO.class))
                .collect(Collectors.toMap(
                        ReplyCountDTO::id,
                        ReplyCountDTO::count,
                        Integer::sum))
                .forEach(commentRepository::updateReplyCount);

        // Clear processed entries
        redis.opsForHash().delete(REDIS_REPLY_COUNT_KEY, processedKeys.toArray());
    }
}
