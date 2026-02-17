package org.ngelmakproject.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.ngelmakproject.domain.Channel;
import org.ngelmakproject.domain.Feed;
import org.ngelmakproject.domain.Post;
import org.ngelmakproject.domain.Post.Status;
import org.ngelmakproject.domain.Reaction;
import org.ngelmakproject.repository.FeedRepository;
import org.ngelmakproject.repository.MembershipRepository;
import org.ngelmakproject.repository.PostRepository;
import org.ngelmakproject.repository.ReactionRepository;
import org.ngelmakproject.web.rest.dto.FeedDTO;
import org.ngelmakproject.web.rest.dto.PageDTO;
import org.ngelmakproject.web.rest.dto.PostDTO;
import org.ngelmakproject.web.rest.dto.ReactionSummaryDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service Implementation for managing
 * {@link org.ngelmakproject.domain.Feed}.
 */
@Service
@Transactional
public class FeedService {

    private static final Logger log = LoggerFactory.getLogger(FeedService.class);

    private final FeedRepository feedRepository;
    private final ReactionRepository reactionRepository;
    private final PostRepository postRepository;
    private final ChannelService channelService;
    private final MembershipRepository membershipRepository;

    public FeedService(FeedRepository feedRepository, ReactionRepository reactionRepository,
            ChannelService channelService,
            MembershipRepository membershipRepository, PostRepository postRepository) {
        this.feedRepository = feedRepository;
        this.reactionRepository = reactionRepository;
        this.channelService = channelService;
        this.membershipRepository = membershipRepository;
        this.postRepository = postRepository;
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
        List<Feed> feeds = membershipRepository.findByFollowing(post.getChannel()).stream().map(membership -> {
            Feed feed = new Feed();
            feed.setFeedOwner(membership.getFollower());
            feed.setPost(post);
            return feed;
        }).collect(Collectors.toList());
        feedRepository.saveAll(feeds);
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