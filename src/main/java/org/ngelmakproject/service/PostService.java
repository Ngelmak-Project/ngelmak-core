package org.ngelmakproject.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import org.ngelmakproject.config.Constants;
import org.ngelmakproject.domain.Channel;
import org.ngelmakproject.domain.Feed;
import org.ngelmakproject.domain.File;
import org.ngelmakproject.domain.Post;
import org.ngelmakproject.domain.Post.Status;
import org.ngelmakproject.domain.Reaction;
import org.ngelmakproject.repository.FeedRepository;
import org.ngelmakproject.repository.PostRepository;
import org.ngelmakproject.repository.ReactionRepository;
import org.ngelmakproject.repository.SubscriptionRepository;
import org.ngelmakproject.repository.projection.PostProjection;
import org.ngelmakproject.service.cache.PostRedisService;
import org.ngelmakproject.web.rest.dto.ActiveChannel;
import org.ngelmakproject.web.rest.dto.FeedPageDTO;
import org.ngelmakproject.web.rest.dto.PageDTO;
import org.ngelmakproject.web.rest.dto.PostDTO;
import org.ngelmakproject.web.rest.dto.ReactionSummaryDTO;
import org.ngelmakproject.web.rest.dto.SortDTO;
import org.ngelmakproject.web.rest.dto.Trending;
import org.ngelmakproject.web.rest.errors.BadRequestAlertException;
import org.ngelmakproject.web.rest.errors.ChannelNotFoundException;
import org.ngelmakproject.web.rest.errors.ResourceNotFoundException;
import org.ngelmakproject.web.rest.errors.UnauthorizedResourceAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Service for retrieving and managing {@link Post} entities.
 *
 * <p>
 * <strong>Note:</strong> This service does not directly persist posts to the
 * database. Write operations are delegated to {@code PostRedisService}, which
 * stores updates in Redis and handles asynchronous or deferred persistence.
 * </p>
 */
@Service
@Transactional(readOnly = true)
public class PostService {

    private static final Logger log = LoggerFactory.getLogger(PostService.class);

    private static final String ENTITY_NAME = "post";

    // Window offsets in seconds for feed expansion: 1 year, 2 years, 3 years.
    private static final long[] WINDOW_OFFSETS = { 1, 2, 3 }; // days back from now

    private final PostRepository postRepository;
    private final FileService fileService;
    private final ChannelService channelService;
    private final ReactionRepository reactionRepository;
    private final FeedRepository feedRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PostRedisService postRedisService;

    PostService(PostRepository postRepository,
            FileService fileService,
            ReactionRepository reactionRepository,
            ChannelService channelService,
            FeedRepository feedRepository,
            SubscriptionRepository subscriptionRepository,
            PostRedisService postRedisService) {
        this.postRepository = postRepository;
        this.reactionRepository = reactionRepository;
        this.feedRepository = feedRepository;
        this.fileService = fileService;
        this.subscriptionRepository = subscriptionRepository;
        this.channelService = channelService;
        this.postRedisService = postRedisService;
    }

    /**
     * Saves a new Post.
     *
     * @param post   the Post to create
     * @param medias media files attached to the post
     * @param covers cover images attached to the post
     * @return the persisted Post
     */
    public Post save(Post post, List<MultipartFile> medias, List<MultipartFile> covers) {
        log.debug("Request to save Post : {} | {}x file(s) and {}x cover(s)",
                post, medias.size(), covers.size());

        // Validate content
        validatePostContent(post.getContent());
        return channelService.findOneByCurrentUser()
                .map(channel -> {
                    // Save media files
                    List<File> files = fileService.save(medias, covers);
                    // Prepare entity
                    post.status(Status.VALIDATED)
                            .visible(post.getVisible() != null ? post.getVisible() : true)
                            .at(Instant.now())
                            .files(new HashSet<>(files))
                            .channel(channel);
                    // Save to Redis
                    postRedisService.queueCreate(post);
                    return post; // return immediately
                })
                .orElseThrow(ChannelNotFoundException::new);
    }

    /**
     * Updates an existing Post.
     * May also delete files listed in deletedMedias.
     *
     * @param post          the Post containing updated fields
     * @param deletedMedias files to remove
     * @param medias        new media files to add
     * @param covers        new cover files to add
     * @return the updated Post
     */
    public Post update(Post post, List<File> deletedMedias,
            List<MultipartFile> medias, List<MultipartFile> covers) {
        log.debug("Request to update Post : {} | {}x file(s), {}x cover(s), {}x to delete", post, medias.size(),
                covers.size(), deletedMedias.size());

        // Validate content
        validatePostContent(post.getContent());

        var channel = channelService.findOneByCurrentUser().orElseThrow(ChannelNotFoundException::new);
        return postRepository.findById(post.getId())
                .map(existing -> {
                    // Ownership check
                    if (!channel.getId().equals(existing.getChannel().getId())) {
                        throw new UnauthorizedResourceAccessException(
                                channel.getUser(), existing.getId(), ENTITY_NAME);
                    }

                    // Save new files
                    List<File> newFiles = fileService.save(medias, covers);
                    existing.getFiles().addAll(newFiles);
                    // Apply updates
                    if (post.getKeywords() != null)
                        existing.setKeywords(post.getKeywords());
                    if (post.getVisible() != null)
                        existing.setVisible(post.getVisible());
                    if (post.getContent() != null)
                        existing.setContent(post.getContent());
                    if (post.getStatus() != null)
                        existing.setStatus(post.getStatus());
                    existing.setLastUpdate(Instant.now());

                    // Save to Redis
                    postRedisService.queueUpdate(post);

                    // Delete removed files (irreversible)
                    fileService.delete(deletedMedias);
                    return existing;
                })
                .orElseThrow(
                        () -> new ResourceNotFoundException("Entity not found", ENTITY_NAME,
                                "idnotfound"));
    }

    /**
     * Validate post content for creation or update.
     * 
     * @param content the content to validate
     * @throws BadRequestAlertException if the content is null, blank, or exceeds
     *                                  the maximum allowed length.
     */
    private void validatePostContent(String content) {
        if (content == null || content.isBlank()) {
            throw new BadRequestAlertException(
                    "Content cannot be empty.",
                    ENTITY_NAME,
                    "contentEmpty");
        }
        if (content.length() > Constants.MAX_POST_LENGTH) {
            throw new BadRequestAlertException(
                    "Content too long (> " + Constants.MAX_POST_LENGTH + " characters).",
                    ENTITY_NAME,
                    "contentTooLong");
        }
    }

    /**
     * Get one post by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    public Optional<Post> findOne(Long id) {
        log.debug("Request to get Post : {}", id);
        return postRepository.findById(id);
    }

    /**
     * Delete the post by id.
     *
     * @param id the id of the entity.
     */
    public void delete(Long id) {
        log.debug("Request to delete Comment : {}", id);
        var channel = channelService.findOneByCurrentUser()
                .orElseThrow(ChannelNotFoundException::new);

        postRepository.findProjectedById(id).ifPresent(projection -> {
            // Authorization check: ensure the comment belongs to the current user
            if (!channel.getId().equals(projection.getChannelId())) {
                throw new UnauthorizedResourceAccessException(
                        channel.getUser(), id, ENTITY_NAME);
            }
            // No pending CREATE found → queue a DELETE operation
            postRedisService.queueDelete(id);
        });
    }

    /**
     * Retrieves a pageable list of all posts enriched with:
     * <p>
     * - minimal channel information
     * - attached files
     * - aggregated reaction summaries (emoji → count + current user reaction)
     * </p>
     *
     * @param pageable
     * @return
     */
    public PageDTO<PostDTO> getPostByAuthenticatedUser(Pageable pageable) {
        Channel channel = channelService.findOneByCurrentUser().orElseThrow(ChannelNotFoundException::new);
        List<Post> posts = this.postRepository.findByChannel(
                channel.getId(),
                pageable).getContent();
        var postDTOs = filloutReactions(posts, channel.getId());
        Page<PostDTO> page = new PageImpl<>(postDTOs, pageable, postDTOs.size());
        return PageDTO.from(page);
    }

    /**
     * Retrieves a pageable list of validated posts enriched with:
     * <p>
     * - minimal channel information
     * - attached files
     * - aggregated reaction summaries (emoji → count + current user reaction)
     *
     * @param channelId id of the channel to which the posts belong.
     * @param pageable
     * @return
     */
    public PageDTO<PostDTO> getPostByChannel(Long channelId, Pageable pageable) {
        // 1. Fetch post entries with channels, and files
        List<Post> posts = this.postRepository.findByChannelAndStatus(
                channelId,
                Status.VALIDATED,
                pageable).getContent();
        var postDTOs = filloutReactions(posts, channelId);
        Page<PostDTO> page = new PageImpl<>(postDTOs, pageable, postDTOs.size());
        return PageDTO.from(page);
    }

    /**
     * This method avoids N+1 queries by:
     * <p>
     * 1. Fetching posts with channel + files in a single query
     * 2. Fetching all reactions for all posts in one bulk query
     * 3. Building reaction summaries in memory
     * 4. Mapping everything into PostDTO objects
     * 
     * @param posts
     * @param channelId
     * @return
     */
    private List<PostDTO> filloutReactions(List<Post> posts, Long channelId) {
        // Extract post IDs
        List<Long> postIds = posts.stream().map(Post::getId).toList();
        // 2. Bulk fetch reactions for all posts in the feed
        List<Reaction> reactions = reactionRepository.findByPostIds(postIds);
        // 3. Group reactions by postId
        Map<Long, List<Reaction>> reactionsByPost = ReactionService.groupReactionsByPost(reactions);
        // 4. Map post entries to DTOs
        List<PostDTO> postDTOs = posts.stream().map(post -> {
            List<Reaction> postReactions = reactionsByPost.getOrDefault(post.getId(), List.of());
            ReactionSummaryDTO summary = ReactionSummaryDTO.from(postReactions, channelId);
            return PostDTO.from(post, summary);
        }).toList();

        return postDTOs;
    }

    /**
     * Builds a personalized feed for the current user by combining:
     * - Posts from the user's channel (via Feed entries)
     * - Ranked global posts selected through a scoring algorithm
     * - Aggregated reaction summaries for each post
     *
     * The method resolves the user's channel, fetches feed posts and
     * ranked post IDs, loads the corresponding Post entities, enriches
     * them with reaction data, sorts them by timestamp, and returns the
     * result as a {@link FeedPageDTO}.
     *
     * @param sessionKey a key used for deterministic ranking; generated if null or
     *                   blank
     * @param pageable   pagination information
     * @return a feed page containing enriched posts and pagination metadata
     */
    public FeedPageDTO<PostDTO> getFeed(String sessionKey, Pageable pageable) {
        // If no session key provided → generate timestamp
        String key = (sessionKey == null || sessionKey.isBlank())
                ? String.valueOf(Instant.now().getEpochSecond())
                : sessionKey;
        /**
         * Fetch feed entries with posts, channels, and files for the user's channel.
         */
        Optional<Channel> optional = channelService.findOneByCurrentUser();
        List<Post> posts = new ArrayList<>();
        if (optional.isPresent()) {
            log.debug("Request to retrieve Feeds for Channel {}.", optional.get());
            // Fetch feed entries with posts, channels, and files
            var page = feedRepository.findByFeedOwner(optional.get(), pageable);
            posts.addAll(page.getContent().stream().map(Feed::getPost).toList());
        }

        /**
         * Expands the feed window in 30‑day steps (up to 6 months) until posts are
         * found.
         * This ensures inactive users still see content while keeping the feed fresh.
         * The final window start is persisted in Redis for future requests.
         */
        List<Long> postIds = Collections.emptyList();
        // Retrieve the cached feed window start, or initialize with the last 90 days
        long windowStart = postRedisService.getWindowSession(key).orElseGet(() -> {
            long start = Instant.now().minus(90, ChronoUnit.DAYS).getEpochSecond();
            postRedisService.setWindowSession(key, start);
            return start;
        });
        long originalWindowStart = windowStart;
        boolean expanded = false;
        for (long days : WINDOW_OFFSETS) {
            long since = Instant.now().minus(days, ChronoUnit.YEARS).getEpochSecond();
            postIds = postRepository.fetchFeedPostIds(
                    key,
                    since,
                    pageable.getPageSize(),
                    (int) pageable.getOffset());
            if (!postIds.isEmpty()) {
                windowStart = since; // use the successful window
                expanded = (windowStart != originalWindowStart);
                break;
            }
        }
        // Save updated window only if it changed
        if (expanded) {
            postRedisService.setWindowSession(key, windowStart);
        }
        // Fetch posts with channels, files, by IDs (avoids N+1).
        posts.addAll(postRepository.findAllByIdIn(postIds));
        // Shuffle posts from the channels followed with those from the activity feed.
        Collections.shuffle(posts);

        /**
         * Bulk fetch reactions for all posts in the feed to avoid N+1 queries.
         */
        List<Reaction> reactions = reactionRepository.findByPostIds(postIds);
        // Group reactions by postId
        Map<Long, List<Reaction>> reactionsByPost = ReactionService.groupReactionsByPost(reactions);
        // Map feed entries to DTOs
        List<PostDTO> feeds = posts.stream().map(post -> {
            List<Reaction> postReactions = reactionsByPost.getOrDefault(post.getId(), List.of());
            ReactionSummaryDTO summary = ReactionSummaryDTO.from(postReactions,
                    optional.map(Channel::getId).orElse(null));
            return PostDTO.from(post, summary);
        }).toList();

        return new FeedPageDTO<PostDTO>(feeds, sessionKey, pageable.getPageNumber(),
                pageable.getSort().stream()
                        .map(order -> new SortDTO(order.getProperty(), order.getDirection().name()))
                        .toList());
    }

    /**
     * This method performs a full-text search on posts based on the provided query
     * and pagination information. It retrieves posts that match the search
     * criteria, along with their associated channels, files, and reactions. The
     * method avoids N+1 queries by fetching all necessary data in bulk and then
     * mapping it into PostDTO objects.
     * 
     * @param query    the full-text search query to filter posts.
     * @param pageable the pagination information.
     * @return the paginated list of posts matching the search criteria.
     */
    public FeedPageDTO<PostDTO> searchFullText(String query, Pageable pageable) {
        // 1. Fetch feed entries with posts, channels, and files
        Optional<Channel> optional = channelService.findOneByCurrentUser();
        List<Post> posts = new ArrayList<>();
        if (optional.isPresent()) {
            log.debug("Request to retrieve Feeds for Channel {}.", optional.get());
            // Fetch feed entries with posts, channels, and files
            var page = feedRepository.findByFeedOwner(optional.get(), pageable);
            posts.addAll(page.getContent().stream().map(Feed::getPost).toList());
        }
        // 2. Fetch posts.
        List<Long> postIds = postRepository.searchFullText(
                query,
                pageable.getPageSize(),
                (int) pageable.getOffset());
        posts.addAll(postRepository.findAllByIdIn(postIds));
        //
        posts.sort((a, b) -> {
            return -1 * a.getAt().compareTo(b.getAt());
        });

        // Bulk fetch reactions for all posts in the feed
        List<Reaction> reactions = reactionRepository.findByPostIds(postIds);
        // Group reactions by postId
        Map<Long, List<Reaction>> reactionsByPost = ReactionService.groupReactionsByPost(reactions);
        // Map feed entries to DTOs
        List<PostDTO> feeds = posts.stream().map(post -> {
            List<Reaction> postReactions = reactionsByPost.getOrDefault(post.getId(), List.of());
            ReactionSummaryDTO summary = ReactionSummaryDTO.from(postReactions,
                    optional.map(Channel::getId).orElse(null));
            return PostDTO.from(post, summary);
        }).toList();

        return new FeedPageDTO<PostDTO>(feeds, null, pageable.getPageNumber(),
                pageable.getSort().stream()
                        .map(order -> new SortDTO(order.getProperty(), order.getDirection().name()))
                        .toList());
    }

    /**
     * Fetches trending data including most active channels and most
     * commented/trending posts.
     * 
     * Performs a single bulk fetch of reactions for all posts to avoid N+1 queries.
     * 
     * @return Trending object containing top channels and post lists with reaction
     *         summaries.
     */
    public Trending getTrending() {
        Trending trending = postRedisService.getTrending().orElseGet(() -> {
            log.warn("🦋 Cache miss for trending, fetching from database");

            List<Long> trendingPostIds = fetchPostsWithFallback(
                    since -> postRepository.trendingPosts(since, PageRequest.of(0, 5)));

            List<Long> mostEngagedPostIds = fetchPostsWithFallback(
                    since -> postRepository.mostEngagedPosts(since, PageRequest.of(0, 5)));

            List<Long> postIds = Stream.concat(
                    mostEngagedPostIds.stream(),
                    trendingPostIds.stream()).toList();

            // Find post by Id
            List<Post> posts = postRepository.findAllById(postIds);

            // Single bulk fetch for all reactions
            List<Reaction> reactions = reactionRepository.findByPostIds(postIds);
            Map<Long, List<Reaction>> reactionsByPost = ReactionService.groupReactionsByPost(reactions);

            // Map both lists to DTOs
            List<PostDTO> mostEngagedPostDTOs = posts.stream()
                    .filter(post -> mostEngagedPostIds.contains(post.getId()))
                    .map(post -> {
                        List<Reaction> postReactions = reactionsByPost.getOrDefault(post.getId(), List.of());
                        ReactionSummaryDTO summary = ReactionSummaryDTO.from(postReactions, null);
                        return PostDTO.from(post, summary);
                    })
                    .toList();

            List<PostDTO> trendingPostDTOs = posts.stream()
                    .filter(post -> trendingPostIds.contains(post.getId()))
                    .map(post -> {
                        List<Reaction> postReactions = reactionsByPost.getOrDefault(post.getId(), List.of());
                        ReactionSummaryDTO summary = ReactionSummaryDTO.from(postReactions, null);
                        return PostDTO.from(post, summary);
                    })
                    .toList();

            List<ActiveChannel> topActiveChannels = channelService.getActiveChannels();

            Trending t = new Trending(topActiveChannels, trendingPostDTOs, mostEngagedPostDTOs);

            // Save to Redis.
            postRedisService.setTrending(t);

            return t;
        });

        log.debug("🦋 Cache hit for trending : {}", trending);
        return trending;
    }

    /**
     * Fetches posts using progressively extended date ranges if results are empty.
     * Attempts to retrieve posts within 7, 30, and 90 days respectively until
     * non-empty results are found.
     *
     * @param fetcher a function that accepts an Instant (since date) and returns
     *                a list of posts from that date onward
     * @return a list of posts from the earliest successful fetch, or an empty
     *         list if no results are found within 90 days
     */
    private List<Long> fetchPostsWithFallback(Function<Instant, List<Long>> fetcher) {
        long[] dayOffsets = { 7, 30, 90 };

        for (long days : dayOffsets) {
            List<Long> posts = fetcher.apply(
                    Instant.now().minus(days, ChronoUnit.DAYS));
            if (!posts.isEmpty()) {
                return posts;
            }
        }

        return Collections.emptyList();
    }

    /**
     * [TODO]
     * Create a personalized feed for each user based on their connections and
     * recommendations.
     * 
     * <p>
     * "Fan-Out on Write” approach, where each user has their own feed, and new
     * posts are propagated to all followers’ feeds upon creation. This allows fo
     * efficient feed retrieval.
     * </p>
     * 
     */
    @Transactional(readOnly = false)
    @Scheduled(cron = "0 0 3 * * *") // every day at 3 AM
    public void propagatePostToFollowers() {
        // Fetch recent posts created in the last 24 hours
        Instant since = Instant.now().minus(1, ChronoUnit.DAYS);
        List<Post> posts = postRepository.findByAtAfter(since);

        if (posts.isEmpty()) {
            return;
        }

        log.debug("Propagating {} posts to followers", posts.size());
        // Extract channels from posts
        List<Channel> channels = posts.stream()
                .map(Post::getChannel)
                .distinct()
                .toList();

        // Fetch all subscriptions for these channels
        var subscriptions = subscriptionRepository.findBySubscribedToIn(channels);

        // Build feeds
        List<Feed> feeds = new ArrayList<>();
        for (Post post : posts) {
            Channel channel = post.getChannel();
            for (var sub : subscriptions) {
                if (sub.getSubscribedTo().equals(channel)) {
                    Feed feed = new Feed();
                    feed.setFeedOwner(sub.getSubscriber());
                    feed.setPost(post);
                    feeds.add(feed);
                }
            }
        }
        // Save all feeds
        if (!feeds.isEmpty()) {
            feedRepository.saveAll(feeds);
            log.info("Created {} feed entries for {} posts", feeds.size(), posts.size());
        }
    }

    /**
     * Permanently deletes posts that were soft‑deleted more than 7 days ago.
     *
     * <p>
     * This scheduled task performs a two‑phase cleanup:
     * <ul>
     * <li>Fetch expired posts using a lightweight projection (ID + fileId)</li>
     * <li>Delete associated files in batch</li>
     * <li>Hard‑delete the posts using a bulk delete</li>
     * </ul>
     *
     * <p>
     * No entities are loaded during this process. All operations rely on
     * projections and batch operations for maximum efficiency.
     * </p>
     */
    @Transactional(readOnly = false)
    @Scheduled(cron = "0 0 3 * * *") // every day at 3 AM
    public void purgeDeletedComments() {
        Instant cutoff = Instant.now().minus(7, ChronoUnit.DAYS);

        List<PostProjection> posts = postRepository.findExpiredPosts(cutoff);

        if (posts.isEmpty()) {
            return;
        }

        // Extract file IDs
        List<Long> fileIds = posts.stream()
                .flatMap(p -> p.getFiles().stream())
                .map(f -> f.getId())
                .filter(Objects::nonNull)
                .toList();

        if (!fileIds.isEmpty()) {
            fileService.deleteByIds(fileIds);
        }

        // Extract post IDs
        List<Long> postIds = posts.stream()
                .map(PostProjection::getId)
                .toList();

        // Hard delete posts
        postRepository.deleteAllByIdInBatch(postIds);

        log.info("Purged {} posts and {} files older than {}",
                postIds.size(), fileIds.size(), cutoff);
    }
}