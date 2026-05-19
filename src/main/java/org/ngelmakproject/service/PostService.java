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
import org.ngelmakproject.web.rest.dto.FeedDTO;
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
import org.springframework.data.domain.Slice;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Service Implementation for managing
 * {@link org.ngelmakproject.domain.Post}.
 */
@Service
public class PostService {

    private static final Logger log = LoggerFactory.getLogger(PostService.class);

    private static final String ENTITY_NAME = "post";
    private final Instant windowStart = Instant.now().minus(50 * 365, ChronoUnit.DAYS);

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
    @Transactional(readOnly = true)
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
    @Transactional(readOnly = true)
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
    @Transactional(readOnly = true)
    public Optional<Post> findOne(Long id) {
        log.debug("Request to get Post : {}", id);
        return postRepository.findById(id);
    }

    /**
     * Delete the post by id.
     *
     * @param id the id of the entity.
     */
    @Transactional(readOnly = true)
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
     * [TODO]
     * To fetch recommended posts, you can integrate a recommendation engine or
     * machine learning model that analyzes user preferences and suggests relevant
     * content.
     * 
     * @param id
     * @param pageRequest
     * @return
     */
    @Transactional(readOnly = true)
    public Slice<Post> getRecommendedPosts(Pageable pageable) {
        log.debug("Post to get recommended Post");
        return postRepository.findByStatusOrderByAtDesc(Status.VALIDATED, pageable);
    }

    @Transactional(readOnly = true)
    public List<PostDTO> getRecommendedPost() {
        log.debug("Request to get recommended Posts as DTO");
        Channel channel = channelService.findOneByCurrentUser().orElseThrow(ChannelNotFoundException::new);
        List<Post> posts = postRepository.findByStatusOrderByAtDesc(Status.VALIDATED, Pageable.unpaged())
                .getContent();
        return filloutReactions(posts, channel.getId());
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
    @Transactional(readOnly = true)
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
    @Transactional(readOnly = true)
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
    @Transactional(readOnly = true)
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
     * Create a personalized feed for each user based on their connections and
     * recommendations.
     * 
     * <p>
     * "Fan-Out on Write” approach, where each user has their own feed, and new
     * posts are propagated to all followers’ feeds upon creation. This allows fo
     * efficient feed retrieval.
     * </p>
     * 
     * @param post
     */
    public void propagatePostToFollowers(List<Post> posts) {
        log.debug("Propagating {} posts to followers", posts.size());

        if (posts.isEmpty()) {
            return;
        }
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

    @Transactional(readOnly = true)
    public FeedPageDTO<PostDTO> getFeedV3(Pageable pageable, String sessionKey) {
        // If no session key provided → generate timestamp
        if (sessionKey == null || sessionKey.isBlank()) {
            sessionKey = String.valueOf(Instant.now().getEpochSecond());
        }
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
        posts.addAll(postRepository.fetchFeedWithRelations(
                sessionKey,
                windowStart,
                pageable.getPageSize(),
                (int) pageable.getOffset()));
        // Extract post IDs
        List<Long> postIds = posts.stream().map(Post::getId).toList();
        // 2. Bulk fetch reactions for all posts in the feed
        List<Reaction> reactions = reactionRepository.findByPostIds(postIds);
        // 3. Group reactions by postId
        Map<Long, List<Reaction>> reactionsByPost = ReactionService.groupReactionsByPost(reactions);
        // 4. Map feed entries to DTOs
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

    @Transactional(readOnly = true)
    public FeedPageDTO<PostDTO> getFeedV2(Pageable pageable, String sessionKey) {
        // If no session key provided → generate timestamp
        if (sessionKey == null || sessionKey.isBlank()) {
            sessionKey = String.valueOf(Instant.now().getEpochSecond());
        }
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
        posts.addAll(postRepository.fetchFeedWithRelations(
                sessionKey,
                windowStart,
                pageable.getPageSize(),
                (int) pageable.getOffset()));
        //
        posts.sort((a, b) -> {
            return -1 * a.getAt().compareTo(b.getAt());
        });

        // Extract post IDs
        List<Long> postIds = posts.stream().map(Post::getId).toList();
        // 2. Bulk fetch reactions for all posts in the feed
        List<Reaction> reactions = reactionRepository.findByPostIds(postIds);
        // 3. Group reactions by postId
        Map<Long, List<Reaction>> reactionsByPost = ReactionService.groupReactionsByPost(reactions);
        // 4. Map feed entries to DTOs
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

    @Transactional(readOnly = true)
    public FeedPageDTO<PostDTO> getFeed(String sessionKey, Pageable pageable) {
        // If no session key provided → generate timestamp
        if (sessionKey == null || sessionKey.isBlank()) {
            sessionKey = String.valueOf(Instant.now().getEpochSecond());
        }
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
        List<Long> postIds = postRepository.fetchFeedPostIds(
                sessionKey,
                windowStart.getEpochSecond(),
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
    @Transactional(readOnly = true)
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
     * Retrieves a pageable list of validated posts enriched with:
     * - minimal channel information (via EntityGraph on the repository)
     * - attached files (also via EntityGraph)
     * - aggregated reaction summaries (emoji → count + current user reaction)
     * - commentCount already stored on Post (no comment fetching required)
     *
     * <p>
     * This method avoids N+1 queries by:
     * 1. Fetching posts with channel + files in a single query
     * 2. Fetching all reactions for all posts in one bulk query
     * 3. Building reaction summaries in memory
     * 4. Mapping everything into PostDTO objects
     * </p>
     * 
     * @param pageable
     * @return
     */
    @Transactional(readOnly = true)
    public PageDTO<FeedDTO> getFeed(Pageable pageable) {
        // 1. Fetch feed entries with posts, channels, and files
        Optional<Channel> optional = channelService.findOneByCurrentUser();
        List<Feed> feeds = new ArrayList<>();
        if (optional.isPresent()) {
            log.debug("Request to retrieve Feeds for Channel {}.", optional.get());
            // Fetch feed entries with posts, channels, and files
            var page = feedRepository.findByFeedOwner(optional.get(), pageable);
            feeds = new ArrayList<>(page.getContent());
        }
        // 2. Fetch post feeds.
        feeds.addAll(this.postRepository.findByStatusOrderByAtDesc(
                Status.VALIDATED,
                pageable).getContent().stream().map(post -> {
                    var feed = new Feed();
                    feed.setPost(post);
                    return feed;
                }).toList());
        // [TODO] Get recommended posts (assuming a method to fetch recommendations
        feeds.sort((a, b) -> {
            return -1 * a.getPost().getAt().compareTo(b.getPost().getAt());
        });

        // Extract post IDs
        List<Long> postIds = feeds.stream()
                .map(f -> f.getPost().getId())
                .toList();
        // Bulk fetch reactions for all posts in the feed
        List<Reaction> reactions = reactionRepository.findByPostIds(postIds);
        // Group reactions by postId
        Map<Long, List<Reaction>> reactionsByPost = ReactionService.groupReactionsByPost(reactions);
        // Map feed entries to DTOs
        List<FeedDTO> feedDTOs = feeds.stream().map(feed -> {
            var post = feed.getPost();
            List<Reaction> postReactions = reactionsByPost.getOrDefault(post.getId(), List.of());
            ReactionSummaryDTO summary = ReactionSummaryDTO.from(postReactions,
                    optional.map(Channel::getId).orElse(null));
            return FeedDTO.from(feed.getId(), PostDTO.from(post, summary));
        }).toList();

        Page<FeedDTO> page = new PageImpl<>(feedDTOs, pageable, feedDTOs.size());
        return PageDTO.from(page);
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
    @Transactional(readOnly = true)
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
    @Transactional(readOnly = true)
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
    @Transactional
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