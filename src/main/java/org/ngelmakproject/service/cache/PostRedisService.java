package org.ngelmakproject.service.cache;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.ngelmakproject.domain.Post;
import org.ngelmakproject.repository.PostRepository;
import org.ngelmakproject.repository.projection.PostEngagementProjection;
import org.ngelmakproject.web.rest.dto.Trending;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PostRedisService {
    private static final Logger log = LoggerFactory.getLogger(PostRedisService.class);

    private static final String REDIS_FEED_KEY = "feed:scores";
    private static final String REDIS_DIRTY_POSTS_KEY = "feed:dirty-posts";
    private static final String REDIS_CREATE_KEY = "post:create";
    private static final String REDIS_UPDATE_KEY = "post:update";
    private static final String REDIS_DELETE_KEY = "post:delete";
    private static final String REDIS_TRENDING_KEY = "post:trending";
    private static final String REDIS_REPLY_COUNT_KEY = "post:replycount";
    private static final String REDIS_TEMP_ID_MAP_KEY = "post:temp-id-map";

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
        log.debug("📦 Redis | Post saved - {}", value);
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
        log.debug("📦 Redis | Post updated - {}", value);
    }

    /**
     * Enqueues a Post ID for async deletion.
     *
     * @param id the ID of the Post to delete
     */
    public void queueDelete(Long id) {
        redis.opsForHash().put(REDIS_DELETE_KEY, id.toString(), id.toString());
        log.debug("📦 Redis | Post deleted - {}", id);
    }

    /**
     * Enqueues a comment count change for a Post.
     *
     * @param postId the ID of the Post whose comment count is updated
     */
    public void queueCommmentCount(Long postId) {
        // Record to redis for updating reply count.
        redis.opsForHash()
                .put(REDIS_REPLY_COUNT_KEY, postId.toString(), postId.toString());
        log.debug("📦 Redis | Post comment count - {}", postId);
    }

    /**
     * Caches trending data in Redis with a short TTL.
     * 
     * @param trending the Trending object to cache
     */
    public void setTrending(Trending trending) {
        String value = CacheTools.toJson(trending);
        redis.opsForValue().set(REDIS_TRENDING_KEY, value, Duration.ofMinutes(10));
        log.debug("📦 Redis | Post trending cached for 10 minutes");
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
     * Stores a temporary ID to actual ID mapping with automatic expiration.
     *
     * @param tempId   the temporary ID assigned during creation
     * @param actualId the actual JPA-assigned ID after flush
     */
    private void storeTempIdMapping(Long tempId, Long actualId) {
        String key = REDIS_TEMP_ID_MAP_KEY + ":" + tempId;
        redis.opsForValue().set(key, actualId.toString(), Duration.ofMinutes(10));
        log.debug("Stored temp ID mapping {} → {} (10min TTL)", tempId, actualId);
    }

    /**
     * Resolves a post ID by checking if it's a temporary ID with a stored mapping
     * to the actual JPA ID. Returns the original ID if no mapping exists.
     *
     * @param id the ID to resolve (may be temporary)
     * @return the actual JPA ID if a mapping exists, otherwise the original ID
     */
    public Long resolvePostId(Long id) {
        String key = REDIS_TEMP_ID_MAP_KEY + ":" + id;
        String mapped = redis.opsForValue().get(key);
        if (mapped != null) {
            Long actualId = Long.parseLong(mapped);
            log.debug("Resolved temporary ID {} to actual ID {}", id, actualId);
            return actualId;
        }
        return id;
    }

    /**
     * Retrieves pending created posts for a specific channel from Redis.
     * These are posts that have been created but not yet flushed to the database.
     *
     * @param channelId the channel ID to filter by
     * @return list of pending posts for the channel
     */
    public List<Post> getPendingPostsByChannel(Long channelId) {
        Set<Object> createdKeys = redis.opsForHash().keys(REDIS_CREATE_KEY);
        Set<Object> deleteKeys = redis.opsForHash().keys(REDIS_DELETE_KEY);
        List<Post> pendingPosts = new ArrayList<>();

        for (Object key : createdKeys) {
            // Skip if this post is queued for deletion
            if (deleteKeys.contains(key)) {
                continue;
            }

            String json = (String) redis.opsForHash().get(REDIS_CREATE_KEY, key);
            Post post = CacheTools.fromJson(json, Post.class);
            if (post.getChannel().getId().equals(channelId)) {
                pendingPosts.add(post);
            }
        }

        log.debug("Retrieved {} pending posts for channel {}", pendingPosts.size(), channelId);
        return pendingPosts;
    }

    private record PostScoreRecord(
            Long id,
            Double baseScore,
            Double finalScore) {
    }

    /**
     * Recompute base scores for all posts within the last 5 years
     * and update the Redis.
     */
    @Transactional(readOnly = true)
    public void recomputeScores() {
        // Fetch engagement metrics for all posts in the last 5 years.
        List<PostEngagementProjection> engagementMetrics = postRepository.fetchRecentEngagementMetricsByAtAfter(
                Instant.now().minus(5 * 365, ChronoUnit.DAYS));
        // Update Redis with new scores.
        updateScores(engagementMetrics);
    }

    /**
     * Computes and updates the base score for each post using the same
     * formula as the SQL scoring:
     *
     * - Recency (50%): exponential decay, 48h half-life
     * - Engagement (30%): logarithmic scale, capped at 100 comments
     */
    private void updateScores(List<PostEngagementProjection> metrics) {
        Instant now = Instant.now();

        metrics.forEach(p -> {
            // --- RECENCY (50%) ---
            double hoursSince = Duration.between(p.getAt(), now).toHours();
            double recency = Math.exp(-(hoursSince / 48.0)) * 0.50;

            // --- ENGAGEMENT (30%) ---
            // SQL: LN(1 + LEAST(comment_count, 100)) / LN(101)
            int cappedComments = (int) Math.min(p.getCommentCount(), 100);
            double engagement = (Math.log(1.0 + cappedComments) / Math.log(101.0)) * 0.30;

            // --- FINAL BASE SCORE ---
            double baseScore = recency + engagement;

            redis.opsForZSet().add(REDIS_FEED_KEY, p.getId().toString(), baseScore);
        });
    }

    /**
     * Retrieves a pending post by its temporary ID from the CREATE queue.
     *
     * @param tempId the temporary ID of the post
     * @return an Optional containing the post if found in the CREATE queue
     */
    public Optional<Post> getPendingPostById(Long tempId) {
        String json = (String) redis.opsForHash().get(REDIS_CREATE_KEY, tempId.toString());
        if (json != null) {
            Post post = CacheTools.fromJson(json, Post.class);
            log.debug("Found pending post with temporary ID {}", tempId);
            return Optional.of(post);
        }
        return Optional.empty();
    }

    /**
     * Removes a pending post from the CREATE queue without persisting to database.
     * Used when a post is deleted before being flushed.
     *
     * @param tempId the temporary ID of the post to remove
     */
    public void removePendingPost(Long tempId) {
        redis.opsForHash().delete(REDIS_CREATE_KEY, tempId.toString());
        log.debug("Removed pending post with temporary ID {} from CREATE queue", tempId);
    }

    /**
     * Fetch paginated post IDs *with their base scores*.
     * If Redis is empty, recompute scores and retry once.
     */
    public List<Long> getTopPostIdsWithScore(String sessionId, int page, int size) {
        // Compute dynamic window size
        int windowSize = Math.max(500, (page + 1) * size);

        // Fetch base scores from Redis
        Set<ZSetOperations.TypedTuple<String>> raw = redis.opsForZSet().reverseRangeWithScores(REDIS_FEED_KEY, 0,
                windowSize - 1);

        // Auto-refresh if empty
        if (raw == null || raw.isEmpty()) {
            recomputeScores();
            raw = redis.opsForZSet().reverseRangeWithScores(REDIS_FEED_KEY, 0, windowSize - 1);
            if (raw == null || raw.isEmpty()) {
                return List.of();
            }
        }
        log.debug("📦 Redis | Fetched {} posts from Redis for session '{}'", raw.size(), sessionId);

        // Compute final score with session randomness
        List<PostScoreRecord> scored = raw.stream()
                .map(r -> {
                    Long id = Long.parseLong(r.getValue());
                    double base = r.getScore();
                    // Deterministic randomness
                    int hash = Math.abs((sessionId + "-" + id).hashCode());
                    double randomness = (hash % 100) / 100.0;
                    double finalScore = base + randomness * 0.20;
                    return new PostScoreRecord(id, base, finalScore);
                })
                .toList();

        // Sort by finalScore DESC
        scored = scored.stream()
                .sorted((a, b) -> Double.compare(b.finalScore(), a.finalScore()))
                .toList();

        // Apply pagination AFTER sorting
        int start = page * size;
        int end = Math.min(start + size, scored.size());

        if (start >= scored.size()) {
            return List.of();
        }

        return scored.subList(start, end).stream()
                .map(PostScoreRecord::id)
                .toList();
    }

    /**
     * Flushes pending CREATE and UPDATE post operations from Redis to the database.
     * Runs every 20 seconds and persists all queued posts in batch.
     */
    @Transactional
    @Scheduled(fixedDelay = 20, timeUnit = TimeUnit.SECONDS)
    public void flushPendingPosts() {
        List<Post> toSave = new ArrayList<>();
        Map<Long, Post> tempIdToPost = new HashMap<>(); // Track temp IDs

        // Process created posts
        Set<Object> createdKeys = redis.opsForHash().keys(REDIS_CREATE_KEY);
        for (Object key : createdKeys) {
            String json = (String) redis.opsForHash().get(REDIS_CREATE_KEY, key);
            Post newPost = CacheTools.fromJson(json, Post.class);
            Long tempId = newPost.getId(); // Capture the temporary ID
            newPost.setId(null);
            toSave.add(newPost);
            tempIdToPost.put(tempId, newPost); // Store for later mapping

            log.debug("Saved {} post(s)", createdKeys.size());
        }
        // Process updated posts
        Set<Object> updatedKeys = redis.opsForHash().keys(REDIS_UPDATE_KEY);
        for (Object key : updatedKeys) {
            String json = (String) redis.opsForHash().get(REDIS_UPDATE_KEY, key);
            toSave.add(CacheTools.fromJson(json, Post.class));
            log.debug("Updated {} post(s)", updatedKeys.size());
        }

        // Return early if nothing to save
        if (toSave.isEmpty()) {
            log.debug("No pending posts to flush");
            return;
        }

        // Save all and clean up Redis
        List<Post> savedPosts = postRepository.saveAll(toSave);

        // Map temporary IDs to actual IDs for created posts
        for (Post saved : savedPosts) {
            for (Map.Entry<Long, Post> entry : tempIdToPost.entrySet()) {
                if (entry.getValue().getContent().equals(saved.getContent()) &&
                        entry.getValue().getChannel().equals(saved.getChannel())) {
                    // Store mapping with 10-minute TTL
                    storeTempIdMapping(entry.getKey(), saved.getId());
                    break;
                }
            }
        }

        if (!createdKeys.isEmpty()) {
            // Mark all saved posts as dirty
            redis.opsForSet().add(REDIS_DIRTY_POSTS_KEY,
                    createdKeys.stream().map(Object::toString).toArray(String[]::new));
            redis.opsForHash().delete(REDIS_CREATE_KEY, createdKeys.toArray());
        }
        if (!updatedKeys.isEmpty()) {
            redis.opsForHash().delete(REDIS_UPDATE_KEY, updatedKeys.toArray());
        }
        log.debug("Successfully flushed {} pending post(s) to database", toSave.size());
    }

    /**
     * Flushes pending DELETE operations from Redis to the database.
     * Runs every 5 seconds and removes all queued post IDs in batch.
     */
    @Transactional
    @Scheduled(fixedDelay = 5, timeUnit = TimeUnit.SECONDS)
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
            // Also remove from feed and dirty set to prevent stale data.
            redis.opsForZSet().remove(REDIS_FEED_KEY, processedKeys.toArray());
            redis.opsForSet().remove(REDIS_DIRTY_POSTS_KEY, processedKeys.toArray());
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
    public void flushCommentCount() {
        Set<Object> processedKeys = redis.opsForHash().keys(REDIS_REPLY_COUNT_KEY);

        if (processedKeys.isEmpty()) {
            return;
        }

        log.debug("Flushing {} pending comment count operations", processedKeys.size());

        // Aggregate and apply updates in one operation
        Set<Long> postIds = processedKeys.stream()
                .map(k -> (String) redis.opsForHash().get(REDIS_REPLY_COUNT_KEY, k))
                .map(Long::parseLong)
                .collect(Collectors.toSet());
        postRepository.updatePostCommentCount(postIds);

        // Clear processed entries
        if (!processedKeys.isEmpty()) {
            redis.opsForHash().delete(REDIS_REPLY_COUNT_KEY, processedKeys.toArray());
            // Mark affected posts as dirty for score recomputation.
            redis.opsForSet().add(REDIS_DIRTY_POSTS_KEY,
                    processedKeys.stream().map(Object::toString).toArray(String[]::new));
        }
    }

    /**
     * Periodically recomputes base scores for "dirty" posts that have been
     * modified since the last computation.
     * 
     * <p>
     * Runs every 1 minute, checking for post IDs marked as "dirty" in Redis,
     * recomputing their scores based on engagement metrics, and updating the
     * Redis accordingly. Also refreshes scores for recent posts to maintain
     * recency relevance.
     * </p>
     */
    @Transactional(readOnly = true)
    @Scheduled(fixedRate = 1, timeUnit = TimeUnit.MINUTES)
    public void recomputeScoresSmart() {
        boolean redisEmpty = redis.opsForZSet().size(REDIS_FEED_KEY) == 0;

        // If Redis empty → full recompute
        if (redisEmpty) {
            log.debug("Redis feed is empty, performing full recompute");
            recomputeScores();
            return;
        }

        // Pull dirty post IDs
        List<Long> dirtyIds = redis.opsForSet().members(REDIS_DIRTY_POSTS_KEY).stream().map(Long::valueOf).toList();
        if (dirtyIds.isEmpty()) {
            log.debug("No dirty posts to recompute");
            return;
        }
        log.debug("Recomputing scores for {} dirty post(s)", dirtyIds.size());

        // Recompute only dirty posts
        if (dirtyIds != null && !dirtyIds.isEmpty()) {
            // Fetch engagement metrics for dirty posts and update scores.
            List<PostEngagementProjection> metrics = postRepository.fetchEngagementMetricsByPostIds(dirtyIds);
            updateScores(metrics);
            // Clear dirty set
            redis.delete(REDIS_DIRTY_POSTS_KEY);
        }

        // Refresh recency window (last 24 hours)
        List<PostEngagementProjection> recent = postRepository.fetchRecentEngagementMetricsByAtAfter(
                Instant.now().minus(1, ChronoUnit.DAYS));

        updateScores(recent);
    }
}
