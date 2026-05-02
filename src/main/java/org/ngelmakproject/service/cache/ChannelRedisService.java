package org.ngelmakproject.service.cache;

import java.time.Duration;
import java.util.Optional;

import org.ngelmakproject.domain.Channel;
import org.ngelmakproject.repository.ChannelRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class ChannelRedisService {
    private static final Logger log = LoggerFactory.getLogger(ChannelRedisService.class);

    private static final String REDIS_CURRENT_USER_KEY = "channel:userchannel";

    private final ChannelRepository channelRepository;
    private final RedisTemplate<String, String> redis;

    public ChannelRedisService(ChannelRepository channelRepository, RedisTemplate<String, String> redis) {
        this.channelRepository = channelRepository;
        this.redis = redis;
    }

    /**
     * Returns the current user's Channel from Redis cache or loads it from the
     * database.
     * The value is cached for 10 minutes.
     */
    public Optional<Channel> getOrLoadCurrentUserChannel(long userId) {
        String key = REDIS_CURRENT_USER_KEY + ":" + userId;

        // Try Redis
        String json = redis.opsForValue().get(key);
        if (json != null) {
            var cached = CacheTools.fromJson(json, Channel.class);
            log.debug("📦 Redis | Cache hit for user's Channel : {}", cached);
            return Optional.of(cached);
        }

        // Load from DB
        Optional<Channel> channel = channelRepository.findOneByUser(userId);

        // Cache if found
        channel.ifPresent(ch -> redis.opsForValue().set(key, CacheTools.toJson(ch), Duration.ofMinutes(10)));
        log.info("📦 Redis | Cached channel for user {} (10 min)", userId);

        return channel;
    }

    /**
     * Updates the current user's Channel in both the database and Redis cache.
     * The updated value is cached for 10 minutes.
     *
     * @param channel the updated Channel entity
     */
    public void updateCurrentUserChannel(Channel channel) {
        String key = REDIS_CURRENT_USER_KEY + ":" + channel.getUser();
        redis.opsForValue().set(key, CacheTools.toJson(channel), Duration.ofMinutes(10));

        log.info("📦 Redis | Updated cached channel for user {} (10 min)", channel.getUser());
    }
}
