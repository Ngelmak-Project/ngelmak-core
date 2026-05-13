package org.ngelmakproject.service.cache;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.ngelmakproject.domain.Post;
import org.ngelmakproject.repository.PostRepository;
import org.ngelmakproject.web.rest.dto.Trending;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PostRedisService {
    private static final Logger log = LoggerFactory.getLogger(PostRedisService.class);

    private static final String REDIS_CREATE_KEY = "post:create";
    private static final String REDIS_UPDATE_KEY = "post:update";
    private static final String REDIS_DELETE_KEY = "post:delete";
    private static final String REDIS_TRENDING_KEY = "post:trending";
    private static final String REDIS_REPLY_COUNT_KEY = "post:replycount";

    private record ReplyCountDTO(long id, int count) {
    }

    private final PostRepository postRepository;
    private final RedisTemplate<String, String> redis;

    public PostRedisService(PostRepository postRepository, RedisTemplate<String, String> redis) {
        this.postRepository = postRepository;
        this.redis = redis;
    }

    /**
     * Enqueues a new Post for async creation and assigns it a generated ID.
     *
     * @param post the Post entity to enqueue for creation
     */
    public void queueCreate(Post post) {
        Long uuid = CacheTools.generateUUID();
        post.setId(uuid);
        String value = CacheTools.toJson(post);
        redis.opsForHash().put(
                REDIS_CREATE_KEY,
                uuid.toString(),
                value);
        log.info("📦 Redis | Post saved - {}", value);
    }

    /**
     * Enqueues a Post update, replacing any pending create entry for the same ID.
     *
     * @param post the Post entity to enqueue for update
     */
    public void queueUpdate(Post post) {
        // Check if hashKey already exist.
        String hashKey = post.getId().toString();
        if (redis.opsForHash().hasKey(REDIS_CREATE_KEY, hashKey)) {
            redis.opsForHash().delete(REDIS_CREATE_KEY, hashKey);
        }
        String value = CacheTools.toJson(post);
        redis.opsForHash().put(REDIS_UPDATE_KEY, hashKey, value);
        log.info("📦 Redis | Post updated - {}", value);
    }

    /**
     * Enqueues a Post ID for async deletion.
     *
     * @param id the ID of the Post to delete
     */
    public void queueDelete(Long id) {
        redis.opsForHash().put(REDIS_DELETE_KEY, id.toString(), id);
        log.warn("📦 Redis | Post deleted - {}", id);
    }

    /**
     * Enqueues a comment count change for a Post.
     *
     * @param postId the ID of the Post whose comment count is updated
     * @param count  the delta to apply (positive or negative)
     */
    public void queueCommmentCount(Long postId, int count) {
        // Record to redis for updating reply count.
        String json = CacheTools.toJson(new ReplyCountDTO(postId, count));
        redis.opsForHash()
                .put(REDIS_REPLY_COUNT_KEY, postId.toString(), json);
        log.info("📦 Redis | Post comment count - {} → {}", postId, count);
    }

    /**
     * Caches trending data in Redis with a short TTL.
     * 
     * @param trending the Trending object to cache
     */
    public void setTrending(Trending trending) {
        String value = CacheTools.toJson(trending);
        redis.opsForValue().set(REDIS_TRENDING_KEY, value, Duration.ofMinutes(10));
        log.info("📦 Redis | Post trending cached for 10 minutes");
    }

    /**
     * Retrieves cached trending data if available.
     * 
     * @return an Optional containing the cached Trending data if available
     */
    public Optional<Trending> getTrending() {
        log.debug("Trending...");
        return Optional.ofNullable(redis.opsForValue().get(REDIS_TRENDING_KEY))
                .map(t -> CacheTools.fromJson((String) t, Trending.class));
    }

    /**
     * Flushes pending CREATE and UPDATE post operations from Redis to the database.
     * Runs every 2 seconds and persists all queued posts in batch.
     */
    @Transactional
    @Scheduled(fixedDelay = 2, timeUnit = TimeUnit.SECONDS)
    public void flushPendingPosts() {
        List<Post> toSave = new ArrayList<>();

        // Process created posts
        Set<Object> createdKeys = redis.opsForHash().keys(REDIS_CREATE_KEY);
        for (Object key : createdKeys) {
            String json = (String) redis.opsForHash().get(REDIS_CREATE_KEY, key);
            Post newPost = CacheTools.fromJson(json, Post.class);
            newPost.setId(null);
            toSave.add(newPost);
        }

        // Process updated posts
        Set<Object> updatedKeys = redis.opsForHash().keys(REDIS_UPDATE_KEY);
        for (Object key : updatedKeys) {
            String json = (String) redis.opsForHash().get(REDIS_UPDATE_KEY, key);
            toSave.add(CacheTools.fromJson(json, Post.class));
        }
        if (!updatedKeys.isEmpty()) {
            log.info("Processing {} updated post(s)", updatedKeys.size());
        }

        // Return early if nothing to save
        if (toSave.isEmpty()) {
            log.debug("No pending posts to flush");
            return;
        }

        // Save all and clean up Redis
        try {
            postRepository.saveAll(toSave);
            // Clean up Redis only after successful save
            if (!createdKeys.isEmpty()) {
                redis.opsForHash().delete(REDIS_CREATE_KEY, createdKeys.toArray());
            }
            if (!updatedKeys.isEmpty()) {
                redis.opsForHash().delete(REDIS_UPDATE_KEY, updatedKeys.toArray());
            }
            log.info("Successfully flushed {} pending post(s) to database", toSave.size());
        } catch (Exception e) {
            log.error("Failed to flush {} pending post(s)", toSave.size(), e);
            throw e; // Re-throw since method is @Transactional
        }

        log.info("Successfully flushed {} pending post(s) to database", toSave.size());
    }

    /**
     * Flushes pending DELETE operations from Redis to the database.
     * Runs every 2 seconds and removes all queued post IDs in batch.
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
        postRepository.deleteAllById(toDelete);
        if (!processedKeys.isEmpty()) {
            redis.opsForHash().delete(REDIS_DELETE_KEY, processedKeys.toArray());
        }
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
    public void flushCommentCount() {
        Set<Object> processedKeys = redis.opsForHash().keys(REDIS_REPLY_COUNT_KEY);

        if (processedKeys.isEmpty()) {
            return;
        }

        log.info("Flushing {} pending comment count operations", processedKeys.size());

        // Aggregate and apply updates in one operation
        processedKeys.stream()
                .map(k -> (String) redis.opsForHash().get(REDIS_REPLY_COUNT_KEY, k))
                .map(json -> CacheTools.fromJson(json, ReplyCountDTO.class))
                .collect(Collectors.toMap(
                        ReplyCountDTO::id,
                        ReplyCountDTO::count,
                        Integer::sum))
                .forEach(postRepository::updatePostCommentCount);

        // Clear processed entries
        if (!processedKeys.isEmpty()) {
            redis.opsForHash().delete(REDIS_REPLY_COUNT_KEY, processedKeys.toArray());
        }
    }
}
