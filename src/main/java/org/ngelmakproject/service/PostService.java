package org.ngelmakproject.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.ngelmakproject.domain.Channel;
import org.ngelmakproject.domain.Feed;
import org.ngelmakproject.domain.File;
import org.ngelmakproject.domain.Post;
import org.ngelmakproject.domain.Post.Status;
import org.ngelmakproject.domain.Post.Visibility;
import org.ngelmakproject.domain.Reaction;
import org.ngelmakproject.repository.FeedRepository;
import org.ngelmakproject.repository.PostRepository;
import org.ngelmakproject.repository.ReactionRepository;
import org.ngelmakproject.repository.SubscriptionRepository;
import org.ngelmakproject.web.rest.dto.FeedDTO;
import org.ngelmakproject.web.rest.dto.FeedPageDTO;
import org.ngelmakproject.web.rest.dto.PageDTO;
import org.ngelmakproject.web.rest.dto.PostDTO;
import org.ngelmakproject.web.rest.dto.ReactionSummaryDTO;
import org.ngelmakproject.web.rest.dto.SortDTO;
import org.ngelmakproject.web.rest.errors.BadRequestAlertException;
import org.ngelmakproject.web.rest.errors.ChannelNotFoundException;
import org.ngelmakproject.web.rest.errors.ResourceNotFoundException;
import org.ngelmakproject.web.rest.errors.UnauthorizedResourceAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;

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
    private final EntityManager entityManager;
    private final FeedRepository feedRepository;
    private final SubscriptionRepository subscriptionRepository;

    PostService(PostRepository postRepository,
            FileService fileService,
            ReactionRepository reactionRepository,
            ChannelService channelService,
            FeedRepository feedRepository,
            SubscriptionRepository subscriptionRepository,
            EntityManager entityManager) {
        this.postRepository = postRepository;
        this.reactionRepository = reactionRepository;
        this.feedRepository = feedRepository;
        this.fileService = fileService;
        this.subscriptionRepository = subscriptionRepository;
        this.channelService = channelService;
        this.entityManager = entityManager;
    }

    /**
     * Save a post.
     *
     * @param post the entity to save.
     * @return the persisted entity.
     */
    @Transactional
    public Post save(Post post, List<MultipartFile> medias, List<MultipartFile> covers) {
        log.debug("Request to save Post : {} | {}x file(s) and {}x cover(s)", post, medias.size(), covers.size());
        if (post.getContent().length() > 3000) {
            throw new BadRequestAlertException("Contenu trop long > 3000 caractères.", ENTITY_NAME, "contentTooLong");
        }
        return channelService.findOneByCurrentUser().map(channel -> {
            /* 1. we start by saving the files if exists */
            List<File> files = fileService.save(medias, covers);
            /* 2. then save the post with the attachments */
            // [TODO] we will need to change the default status to match with the fact that
            // some users can create posts that bypass some step validations.
            post.status(Status.VALIDATED) // default status is PENDING
                    .at(Instant.now()) // set the current time
                    .files(new HashSet<File>(files)) // attach files to the post
                    .channel(channel); // set the current connected user as owner of the post.
            return postRepository.save(post);
        }).orElseThrow(ChannelNotFoundException::new);
    }

    /**
     * Update a post.
     * This function can eventually delete some files through the given
     * deletedFiles variable.
     *
     * @param post the entity to save.
     * @return the persisted entity.
     */
    public Post update(Post post, List<File> deletedMedias,
            List<MultipartFile> medias, List<MultipartFile> covers) {
        log.debug("Request to update Post : {} | {}x file(s), {}x cover(s), and {}x to be deleted", post, medias.size(),
                covers.size(), deletedMedias.size());
        if (post.getContent().length() > 3000) {
            throw new BadRequestAlertException("Contenu trop long > 3000 caractères.", ENTITY_NAME, "contentTooLong");
        }
        return channelService.findOneByCurrentUser().map(channel -> {
            return postRepository.findById(post.getId())
                    .map(existingPost -> {
                        if (channel.getId() != existingPost.getChannel().getId()) {
                            throw new UnauthorizedResourceAccessException(channel.getUser(), existingPost.getId(),
                                    ENTITY_NAME);
                        }
                        /* 1. we start by saving the files if exists */
                        List<File> files = fileService.save(medias, covers);
                        /* 2. update the existing post */
                        existingPost.getFiles().addAll(files);
                        if (post.getKeywords() != null) {
                            existingPost.setKeywords(post.getKeywords());
                        }
                        if (post.getAt() != null) {
                            existingPost.setAt(post.getAt());
                        }
                        if (post.getLastUpdate() != null) {
                            existingPost.setLastUpdate(post.getLastUpdate());
                        }
                        if (post.getVisibility() != null) {
                            existingPost.setVisibility(post.getVisibility());
                        }
                        if (post.getContent() != null) {
                            existingPost.setContent(post.getContent());
                        }
                        if (post.getStatus() != null) {
                            existingPost.setStatus(post.getStatus());
                        }
                        postRepository.save(existingPost);
                        /* 3. delete removed files */
                        // [WARN] make sure to delete files only when all other actions are successfully
                        // completed. Since the deleted actions of file may have actions that cannot be
                        // cancelled, like removing files.
                        fileService.delete(deletedMedias);

                        return existingPost;
                    })
                    .orElseThrow(() -> new ResourceNotFoundException("Entity not found", ENTITY_NAME, "idnotfound"));
        }).orElseThrow(ChannelNotFoundException::new);
    }

    /**
     * Get all the posts.
     *
     * @param pageable the pagination information.
     * @return the list of entities.
     */
    @Transactional(readOnly = true)
    public PageDTO<Post> findAll(String query, Pageable pageable) {
        log.debug("Request to get all Posts");
        if (query.length() > 5) {
            return fullTextSearch(query, pageable);
        }
        Slice<Post> page = postRepository.findByStatusOrderByAtDesc(Status.VALIDATED, pageable);
        return PageDTO.from(page);
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

    @Transactional(readOnly = true)
    public PageDTO<Post> fullTextSearch(String fullText, Pageable pageable) {
        String sqlQuery = "SELECT " +
                "  full_search.*, " +
                "  p.id AS post_reference_id, " +
                "  p.title AS post_reference_title, " +
                "  p.content AS post_reference_content, " +
                "  a.name AS channel_name " +
                "FROM ( " +
                "  SELECT p.* FROM ( " +
                "    SELECT *, ts_rank_cd(textsearchable_index_col, query) AS rank " +
                "    FROM nk_post, websearch_to_tsquery('french', :fullText) query " +
                "    WHERE status = 'VALIDATED' AND textsearchable_index_col @@ query " +
                "    ) AS p " +
                "  LEFT JOIN (SELECT id, ts_rank_cd(textsearchable_index_col, query) AS rank " +
                "  FROM nk_post, websearch_to_tsquery('french', :fullText) query " +
                "  WHERE textsearchable_index_col @@ query) AS a " +
                "  ON p.channel_id = a.id " +
                "  ORDER BY a.rank,p.rank DESC " +
                "  LIMIT :limit " +
                "  OFFSET :offset " +
                ") AS full_search " +
                "LEFT JOIN nk_post AS p ON full_search.post_reference_id = p.id " +
                "LEFT JOIN nk_channel AS a ON a.id = p.channel_id";
        Query query = entityManager.createNativeQuery(sqlQuery, Tuple.class);
        query.setParameter("fullText", fullText);
        query.setParameter("limit", pageable.getPageSize());
        query.setParameter("offset", pageable.getOffset());
        List<Tuple> result = query.getResultList();
        List<Post> posts = result.stream()
                .map(t -> {
                    Post post = new Post();
                    var channel = new Channel();
                    channel.setId(t.get("channel_id", Long.class));
                    channel.setName(t.get("channel_name", String.class));
                    // java.time.Instant
                    post.id(t.get("id", Long.class))
                            .keywords(t.get("keywords", String.class))
                            .at(t.get("at", Instant.class))
                            .lastUpdate(t.get("last_update", Instant.class))
                            .visibility(Visibility.valueOf(t.get("visibility", String.class)))
                            .content(t.get("content", String.class))
                            .status(Status.valueOf(t.get("status", String.class)))
                            .channel(channel)
                            .postReply(
                                    new Post()
                                            .id(t.get("post_reference_id", Long.class))
                                            .content(t.get("post_reference_content", String.class)));
                    return post;
                })
                .collect(Collectors.toList());
        Page<Post> page = new PageImpl<>(posts, pageable, posts.size());
        return PageDTO.from(page);
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

}