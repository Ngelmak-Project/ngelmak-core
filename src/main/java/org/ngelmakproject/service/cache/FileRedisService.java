package org.ngelmakproject.service.cache;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.ngelmakproject.domain.File;
import org.ngelmakproject.repository.FileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FileRedisService {
    private static final Logger log = LoggerFactory.getLogger(FileRedisService.class);

    private static final String REDIS_USAGE_COUNT_KEY = "file:usagecount";
    private static final String REDIS_USAGE_COUNT_URL_KEY = "file:usagecount:url";

    private final FileRepository fileRepository;
    private final RedisTemplate<String, String> redis;

    public FileRedisService(FileRepository fileRepository,
            RedisTemplate<String, String> redis) {
        this.fileRepository = fileRepository;
        this.redis = redis;
    }

    private record PendingUsageCountById(Long id, int count) {
    }

    private record PendingUsageCountByUrl(String url, int count) {
    }

    /**
     * Enqueues a file usage count change for a File.
     *
     * @param id    the ID of the File whose usage count is being updated
     * @param count the change in usage count (positive for increment, negative for
     *              decrement)
     */
    public void queueUsageCount(Long id, int count) {
        // Record to redis for updating usage count.
        var usageUpdate = new PendingUsageCountById(id, count);
        String json = CacheTools.toJson(usageUpdate);
        redis.opsForHash()
                .put(REDIS_USAGE_COUNT_KEY, id.toString(), json);
        log.info("📦 Redis | File usage count updated for ID: {}, Count: {}", id, count);
    }

    /**
     * Enqueues a file usage count change for a File.
     *
     * @param id    the ID of the File whose usage count is being updated
     * @param count the change in usage count (positive for increment, negative for
     *              decrement)
     */
    public void queueUsageCount(String url, int count) {
        // Record to redis for updating usage count.
        var usageUpdate = new PendingUsageCountByUrl(url, count);
        String json = CacheTools.toJson(usageUpdate);
        redis.opsForHash()
                .put(REDIS_USAGE_COUNT_URL_KEY, url, json);
        log.info("📦 Redis | File usage count updated for URL: {}, Count: {}", url, count);
    }

    /**
     * Periodically flushes aggregated usage count updates from Redis to the
     * database.
     * 
     * <p>
     * Runs every 10 minutes, retrieving pending operations from Redis for both
     * file IDs and URLs, aggregating count changes, and applying a single batch
     * update to the database.
     * </p>
     */
    @Transactional
    @Scheduled(fixedDelay = 10, timeUnit = TimeUnit.MINUTES)
    public void flushUsageCount() {
        // Fetch from both Redis keys
        Set<Object> idKeys = redis.opsForHash().keys(REDIS_USAGE_COUNT_KEY);
        Set<Object> urlKeys = redis.opsForHash().keys(REDIS_USAGE_COUNT_URL_KEY);

        boolean hasFileIdUpdates = !idKeys.isEmpty();
        boolean hasUrlUpdates = !urlKeys.isEmpty();

        if (!hasFileIdUpdates && !hasUrlUpdates) {
            return;
        }

        log.info("Flushing {} file ID and {} URL usage count operations",
                idKeys.size(), urlKeys.size());

        // Combine both sources and aggregate
        Map<Long, Integer> aggregatedUpdates = new HashMap<>();

        // Process file ID updates
        if (hasFileIdUpdates) {
            idKeys.stream()
                    .map(k -> (String) redis.opsForHash().get(REDIS_USAGE_COUNT_KEY, k))
                    .map(json -> CacheTools.fromJson(json, PendingUsageCountById.class))
                    .forEach(update -> aggregatedUpdates.merge(update.id(), update.count(), Integer::sum));
        }

        // Process URL updates and resolve to file IDs using findByUrlIn
        if (hasUrlUpdates) {
            List<PendingUsageCountByUrl> urlUpdates = urlKeys.stream()
                    .map(k -> (String) redis.opsForHash().get(REDIS_USAGE_COUNT_URL_KEY, k))
                    .map(json -> CacheTools.fromJson(json, PendingUsageCountByUrl.class))
                    .toList();

            List<String> urls = urlUpdates.stream().map(PendingUsageCountByUrl::url).toList();
            Map<String, Long> urlToFileIdMap = fileRepository.findByUrlIn(urls).stream()
                    .collect(Collectors.toMap(File::getUrl, File::getId));

            urlUpdates.forEach(update -> {
                Long id = urlToFileIdMap.get(update.url());
                if (id != null) {
                    aggregatedUpdates.merge(id, update.count(), Integer::sum);
                }
            });
        }

        // Single batch operation
        aggregatedUpdates.forEach(fileRepository::updateUsageCount);

        // Clear processed entries from Redis
        if (hasFileIdUpdates) {
            redis.opsForHash().delete(REDIS_USAGE_COUNT_KEY, idKeys.toArray());
        }
        if (hasUrlUpdates) {
            redis.opsForHash().delete(REDIS_USAGE_COUNT_URL_KEY, urlKeys.toArray());
        }
    }

}
