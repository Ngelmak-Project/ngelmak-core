package org.ngelmakproject.service.cache;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.ngelmakproject.domain.Comment;
import org.ngelmakproject.repository.CommentRepository;
import org.ngelmakproject.repository.projection.CommentProjection;
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

    private final CommentRepository commentRepository;
    private final RedisTemplate<String, String> redis;
    private final PostRedisService postRedisService;

    public CommentRedisService(CommentRepository commentRepository,
            RedisTemplate<String, String> redis,
            PostRedisService postRedisService) {
        this.commentRepository = commentRepository;
        this.redis = redis;
        this.postRedisService = postRedisService;
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
        log.debug("📦 Redis | Comment saved - {}", value);
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
        log.debug("📦 Redis | Comment updated - {}", value);
    }

    /**
     * Enqueues a Comment ID for async deletion.
     *
     * @param comment the Comment to delete
     */
    public void queueDelete(CommentProjection comment) {
        String value = CacheTools.toJson(comment);
        redis.opsForHash().put(REDIS_DELETE_KEY, comment.getId().toString(), value);
        log.warn("📦 Redis | Comment deleted - {}", value);
    }

    /**
     * Enqueues a comment count change for a Comment.
     *
     * @param commentId the ID of the Comment whose comment count is updated
     */
    public void queueReplyCount(Long commentId) {
        // Record to redis for updating reply count.
        redis.opsForHash()
                .put(REDIS_REPLY_COUNT_KEY, commentId.toString(), commentId.toString());
        log.debug("📦 Redis | Comment comment count - {}", commentId);
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
            // If this comment is a reply, queue an update for the parent comment to refresh
            // its reply count.
            if (newComment.getReplyTo() != null) {
                queueReplyCount(newComment.getReplyTo().getId());
            } else if (newComment.getPost() != null) {
                postRedisService.queueCommmentCount(newComment.getPost().getId());
            }
            log.debug("Saved {} comment(s)", createdKeys.size());
        }
        Set<Object> updatedKeys = redis.opsForHash().keys(REDIS_UPDATE_KEY);
        for (Object key : updatedKeys) {
            String json = (String) redis.opsForHash().get(REDIS_UPDATE_KEY, key);
            toSave.add(CacheTools.fromJson(json, Comment.class));
            log.debug("Updated {} comment(s)", updatedKeys.size());
        }
        if (toSave.isEmpty()) {
            return;
        }

        commentRepository.saveAll(toSave);

        // Only delete if there are keys to delete
        if (!createdKeys.isEmpty()) {
            redis.opsForHash().delete(REDIS_CREATE_KEY, createdKeys.toArray());
        }
        if (!updatedKeys.isEmpty()) {
            redis.opsForHash().delete(REDIS_UPDATE_KEY, updatedKeys.toArray());
        }
        log.debug("Flushing {} pending comment operations", toSave.size());
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
        List<CommentProjection> toDelete = processedKeys.stream()
                .map(k -> (String) redis.opsForHash().get(REDIS_DELETE_KEY, k))
                .map(json -> CacheTools.fromJson(json, CommentProjection.class))
                .toList();

        Set<Long> toDeleteIds = toDelete.stream().map(CommentProjection::getId).collect(Collectors.toSet());
        Set<Long> replyCommentIds = toDelete.stream()
                .map(CommentProjection::getReplyToId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        commentRepository.softDeleteByIds(toDeleteIds, Instant.now());
        commentRepository.updateReplyCount(replyCommentIds);
        toDelete.stream()
                .map(CommentProjection::getPostId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet())
                .forEach(postRedisService::queueCommmentCount);

        if (!processedKeys.isEmpty()) {
            redis.opsForHash().delete(REDIS_DELETE_KEY, processedKeys.toArray());
        }
        log.debug("Removed {} processed operations from Redis", processedKeys.size());
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

        log.debug("Flushing {} pending reply count operations", processedKeys.size());

        // Aggregate and apply updates in one operation
        Set<Long> commentIds = processedKeys.stream()
                .map(k -> (String) redis.opsForHash().get(REDIS_REPLY_COUNT_KEY, k))
                .map(Long::parseLong)
                .collect(Collectors.toSet());
        commentRepository.updateReplyCount(commentIds);

        // Clear processed entries
        if (!processedKeys.isEmpty()) {
            redis.opsForHash().delete(REDIS_REPLY_COUNT_KEY, processedKeys.toArray());
        }
    }
}
