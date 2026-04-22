package org.ngelmakproject.service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Service Implementation for managing
 * {@link org.ngelmakproject.domain.Post}.
 */
@Service
@Transactional
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

    PostService(PostRepository postRepository,
            FileService fileService,
            ReactionRepository reactionRepository,
            ChannelService channelService,
            FeedRepository feedRepository,
            SubscriptionRepository subscriptionRepository) {
        this.postRepository = postRepository;
        this.reactionRepository = reactionRepository;
        this.feedRepository = feedRepository;
        this.fileService = fileService;
        this.subscriptionRepository = subscriptionRepository;
        this.channelService = channelService;
    }

    /**
     * Saves a new Post.
     *
     * @param post   the Post to create
     * @param medias media files attached to the post
     * @param covers cover images attached to the post
     * @return the persisted Post
     */
    @Transactional
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
                            .at(Instant.now())
                            .files(new HashSet<>(files))
                            .channel(channel);
                    // Persist
                    return postRepository.save(post);
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

        log.debug("Request to update Post : {} | {}x file(s), {}x cover(s), {}x to delete",
                post, medias.size(), covers.size(), deletedMedias.size());

        // Validate content
        validatePostContent(post.getContent());
        return channelService.findOneByCurrentUser()
                .map(channel -> postRepository.findById(post.getId())
                        .map(existingPost -> {
                            // Ownership check
                            if (!channel.getId().equals(existingPost.getChannel().getId())) {
                                throw new UnauthorizedResourceAccessException(
                                        channel.getUser(), existingPost.getId(), ENTITY_NAME);
                            }
                            // Save new files
                            List<File> newFiles = fileService.save(medias, covers);
                            existingPost.getFiles().addAll(newFiles);
                            // Apply updates
                            if (post.getKeywords() != null)
                                existingPost.setKeywords(post.getKeywords());
                            if (post.getAt() != null)
                                existingPost.setAt(post.getAt());
                            if (post.getLastUpdate() != null)
                                existingPost.setLastUpdate(post.getLastUpdate());
                            if (post.getVisibility() != null)
                                existingPost.setVisibility(post.getVisibility());
                            if (post.getContent() != null)
                                existingPost.setContent(post.getContent());
                            if (post.getStatus() != null)
                                existingPost.setStatus(post.getStatus());
                            // Persist before deleting files
                            postRepository.save(existingPost);
                            // Delete removed files (irreversible)
                            fileService.delete(deletedMedias);
                            return existingPost;
                        })
                        .orElseThrow(
                                () -> new ResourceNotFoundException("Entity not found", ENTITY_NAME, "idnotfound")))
                .orElseThrow(ChannelNotFoundException::new);
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
    public void delete(Long id) {
        log.debug("Request to delete Post : {}", id);
        throw new RuntimeException("Not Implemented...");
        // postRepository.deleteById(id);
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

    public List<PostDTO> getRecommendedPost() {
        log.debug("Request to get recommended Posts as DTO");
        Channel channel = channelService.findOneByCurrentUser().orElseThrow(ChannelNotFoundException::new);
        List<Post> posts = postRepository.findByStatusOrderByAtDesc(Status.VALIDATED, Pageable.unpaged())
                .getContent();
        return filloutReactions(posts, channel.getId());
    }

    /**
     * Update Post total comments.
     * 
     * <p>
     * This method is responsible of tracking and updating total comments of Posts.
     * <\p>
     * 
     * [TODO] This method later should consider reading from Redis database and
     * update automatically the comment count.
     * It should be handle by a cron
     * 
     * @param postId
     * @param count  could be a positive or negative number.
     */
    // @Scheduled(cron = "0 0 2 * * *") // Run daily at 2 AM
    public void updateCommmentCount(Long postId, Integer count) {
        this.postRepository.updatePostCommentCount(postId, count);
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
     * </p>
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
     * <\p>
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
    public void propagatePostToFollowers(Post post) {
        log.debug("Propagate Post to get all followers.");
        List<Feed> feeds = subscriptionRepository.findBySubscribedTo(post.getChannel().getId()).stream()
                .map(follower -> {
                    Feed feed = new Feed();
                    feed.setFeedOwner(follower.getSubscriber());
                    feed.setPost(post);
                    return feed;
                }).collect(Collectors.toList());
        feedRepository.saveAll(feeds);
    }

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
        List<Long> ids = postRepository.fetchFeedPostIds(
                sessionKey,
                windowStart.getEpochSecond(),
                pageable.getPageSize(),
                (int) pageable.getOffset());
        posts.addAll(postRepository.findAllByIdIn(ids));
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
        List<Long> ids = postRepository.searchFullText(
                query,
                pageable.getPageSize(),
                (int) pageable.getOffset());
        posts.addAll(postRepository.findAllByIdIn(ids));
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
        // 2. Bulk fetch reactions for all posts in the feed
        List<Reaction> reactions = reactionRepository.findByPostIds(postIds);
        // 3. Group reactions by postId
        Map<Long, List<Reaction>> reactionsByPost = ReactionService.groupReactionsByPost(reactions);
        // 4. Map feed entries to DTOs
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
    public Trending getTrending() {
        log.debug("Trending...");
        List<Post> mostCommentedPosts = postRepository.mostCommentedPosts(Instant.now().minus(30, ChronoUnit.DAYS),
                PageRequest.of(0, 5));
        List<Post> trendingPosts = postRepository.trendingPosts(
                Instant.now().minus(30, ChronoUnit.DAYS),
                PageRequest.of(0, 5));

        // Extract post IDs from both lists
        List<Long> postIds = Stream.concat(
                mostCommentedPosts.stream().map(Post::getId),
                trendingPosts.stream().map(Post::getId)).toList();

        // Single bulk fetch for all reactions
        List<Reaction> reactions = reactionRepository.findByPostIds(postIds);
        Map<Long, List<Reaction>> reactionsByPost = ReactionService.groupReactionsByPost(reactions);

        // Map both lists to DTOs
        List<PostDTO> mostCommentedPostDTOs = mostCommentedPosts.stream()
                .map(post -> {
                    List<Reaction> postReactions = reactionsByPost.getOrDefault(post.getId(), List.of());
                    ReactionSummaryDTO summary = ReactionSummaryDTO.from(postReactions, null);
                    return PostDTO.from(post, summary);
                })
                .toList();

        List<PostDTO> trendingPostDTOs = trendingPosts.stream()
                .map(post -> {
                    List<Reaction> postReactions = reactionsByPost.getOrDefault(post.getId(), List.of());
                    ReactionSummaryDTO summary = ReactionSummaryDTO.from(postReactions, null);
                    return PostDTO.from(post, summary);
                })
                .toList();

        List<ActiveChannel> topActiveChannels = channelService.getActiveChannels();

        return new Trending(topActiveChannels, trendingPostDTOs, mostCommentedPostDTOs);

    }
}