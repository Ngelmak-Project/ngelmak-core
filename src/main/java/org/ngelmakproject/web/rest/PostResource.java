package org.ngelmakproject.web.rest;

import java.io.IOException;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.ngelmakproject.domain.File;
import org.ngelmakproject.domain.Post;
import org.ngelmakproject.service.PostService;
import org.ngelmakproject.web.rest.dto.FeedPageDTO;
import org.ngelmakproject.web.rest.dto.PageDTO;
import org.ngelmakproject.web.rest.dto.PostDTO;
import org.ngelmakproject.web.rest.errors.BadRequestAlertException;
import org.ngelmakproject.web.rest.util.HeaderUtil;
import org.ngelmakproject.web.rest.util.ResponseUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * REST controller for managing {@link org.ngelmakproject.domain.Post}.
 */
@RestController
@RequestMapping("/api/posts")
public class PostResource {

    private static final Logger log = LoggerFactory.getLogger(PostResource.class);

    private static final String ENTITY_NAME = "post";

    @Value("${spring.application.name}")
    private String applicationName;

    private final PostService postService;

    public PostResource(PostService postService) {
        this.postService = postService;
    }

    /**
     * {@code POST  /posts} : Create a new post.
     *
     * @param post the post to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with
     *         body the new post, or with status {@code 400 (Bad Request)} if the
     *         post has already an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PostMapping("")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PostDTO> createPost(@RequestPart Post post,
            @RequestPart(required = false) Optional<List<MultipartFile>> _medias,
            @RequestPart(required = false) Optional<List<MultipartFile>> _covers)
            throws URISyntaxException {
        List<MultipartFile> medias = _medias.orElse(List.of());
        List<MultipartFile> covers = _covers.orElse(List.of());
        log.info("REST request to save Post : {} + {}x media(s) and {}x cover(s)", post, medias.size(), covers.size());
        if (post.getId() != null) {
            throw new BadRequestAlertException("A new post cannot already have an ID", ENTITY_NAME, "idexists");
        }
        post = postService.save(post, medias, covers);
        return ResponseEntity.ok().body(PostDTO.from(post, null));
    }

    /**
     * {@code PUT  /posts/:id} : Updates an existing post.
     *
     * @param id   the id of the post to save.
     * @param post the post to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body
     *         the updated post,
     *         or with status {@code 400 (Bad Request)} if the post is not valid,
     *         or with status {@code 500 (Internal Server Error)} if the post
     *         couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     * @throws IOException
     */
    @PutMapping("")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PostDTO> updatePost(
            @RequestPart Post post,
            @RequestPart(required = false) Optional<List<File>> _deletedFiles,
            @RequestPart(required = false) Optional<List<MultipartFile>> _medias,
            @RequestPart(required = false) Optional<List<MultipartFile>> _covers)
            throws URISyntaxException, IOException {
        List<File> deletedFiles = _deletedFiles.orElse(List.of());
        List<MultipartFile> medias = _medias.orElse(List.of());
        List<MultipartFile> covers = _covers.orElse(List.of());
        log.info("REST request to save Post : {} | {}x media(s), {}x cover(s), and {}x to be deleted", post,
                medias.size(), covers.size(), deletedFiles.size());
        if (post.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        post = postService.update(post, deletedFiles, medias, covers);
        return ResponseEntity.ok()
                .body(PostDTO.from(post, null));
    }

    /**
     * {@code GET  /posts/channel/:id} : get all the posts.
     *
     * @param channelId of the Post to get.
     * @param pageable  the pagination information.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list
     *         of posts in body.
     */
    @GetMapping("/channel/{channelId}")
    public ResponseEntity<PageDTO<PostDTO>> getPostByChannel(@PathVariable Long channelId, Pageable pageable) {
        log.info("REST request to get a page of Posts by Channel : {}", channelId);
        PageDTO<PostDTO> page = postService.getPostByChannel(channelId, pageable);
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(60, TimeUnit.SECONDS))
                .body(page);
    }

    /**
     * {@code GET  /posts/me} : get all the posts of the connected user channel.
     *
     * @param pageable the pagination information.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list
     *         of posts in body.
     */
    @GetMapping("/me")
    public ResponseEntity<PageDTO<PostDTO>> getPostByAuthenticatedUser(Pageable pageable) {
        log.debug("REST request to get a page of Posts");
        PageDTO<PostDTO> page = postService.getPostByAuthenticatedUser(pageable);
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(60, TimeUnit.SECONDS))
                .body(page);
    }

    // /**
    // * {@code GET /posts/search?q=} : search posts that match the query.
    // *
    // * @param pageable the pagination information.
    // * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the
    // list
    // * of posts in body.
    // */
    // @GetMapping("/search")
    // public ResponseEntity<PageDTO<Post>> fullTextSearch(@RequestParam("q")
    // String
    // query,
    // Pageable pageable) {
    // log.debug("REST request to search Post : {}", query);
    // return ResponseEntity.ok().cacheControl(CacheControl.maxAge(60,
    // TimeUnit.SECONDS))
    // .body(postService.fullTextSearch(query, pageable));
    // }

    /**
     * {@code GET  /feeds?q=} : retrieve the feed of the connected user, with
     * optional search query.
     * Session key is used to identify the feed session, allowing to keep track of
     * the posts already seen by the user, and to provide a consistent feed across
     * multiple requests. If no session key is provided, a new one is generated
     * based on the current timestamp, ensuring that the user receives a fresh feed.
     *
     * @param pageable the pagination information.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list
     *         of feeds in body.
     */
    @GetMapping("/feeds")
    public ResponseEntity<FeedPageDTO<PostDTO>> getFeeds(
            @RequestParam(value = "q", defaultValue = "") String query,
            @RequestParam(required = false) String sessionKey,
            Pageable pageable) {
        log.debug("REST request to get a page of Feeds : {}, sessionKey={}", query, sessionKey);

        // If no session key provided → generate timestamp
        if (sessionKey == null) {
            sessionKey = String.valueOf(Instant.now().getEpochSecond());
        }

        // If query is blank, get feed, else search in feed.
        FeedPageDTO<PostDTO> pageDTO = query.isBlank() ? postService.getFeed(sessionKey, pageable)
                : postService.searchFullText(query.trim(), pageable);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(60, TimeUnit.SECONDS))
                .body(pageDTO);
    }

    /**
     * {@code GET  /posts/:id} : get the "id" post.
     *
     * @param id the id of the post to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body
     *         the post, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Post> getPost(@PathVariable Long id) {
        log.debug("REST request to get Post : {}", id);
        Optional<Post> post = postService.findOne(id);
        return ResponseUtil.wrapOrNotFound(post);
    }

    /**
     * {@code DELETE  /posts/:id} : delete the "id" post.
     *
     * @param id the id of the post to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> deletePost(@PathVariable("id") Long id) {
        log.debug("REST request to delete Post : {}", id);
        postService.delete(id);
        return ResponseEntity.noContent()
                .headers(HeaderUtil.createEntityDeletionAlert(applicationName, ENTITY_NAME, id.toString()))
                .build();
    }
}