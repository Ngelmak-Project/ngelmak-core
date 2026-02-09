package org.ngelmakproject.web.rest;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Optional;

import org.ngelmakproject.domain.Comment;
import org.ngelmakproject.domain.File;
import org.ngelmakproject.repository.CommentRepository;
import org.ngelmakproject.service.CommentService;
import org.ngelmakproject.web.rest.dto.CommentDTO;
import org.ngelmakproject.web.rest.dto.PageDTO;
import org.ngelmakproject.web.rest.errors.BadRequestAlertException;
import org.ngelmakproject.web.rest.util.HeaderUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * REST controller for managing {@link org.ngelmakproject.domain.Comment}.
 */
@RestController
@RequestMapping("/api/comments")
public class CommentResource {

    private static final Logger log = LoggerFactory.getLogger(CommentResource.class);

    private static final String ENTITY_NAME = "comment";

    @Value("${spring.application.name}")
    private String applicationName;

    private final CommentService commentService;
    private final CommentRepository commentRepository;

    public CommentResource(CommentService commentService, CommentRepository commentRepository) {
        this.commentService = commentService;
        this.commentRepository = commentRepository;
    }

    /**
     * {@code POST  /comments} : Create a new comment.
     *
     * @param comment the comment to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with
     *         body the new comment, or with status {@code 400 (Bad Request)} if the
     *         comment has already an ID.
     * @throws URISyntaxException    if the Location URI syntax is incorrect.
     * @throws MalformedURLException
     */
    @PostMapping("")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Comment> createComment(@RequestPart Comment comment,
            @RequestPart(required = false) Optional<MultipartFile> media)
            throws URISyntaxException, MalformedURLException {
        log.info("REST request to save Comment : {} + {}x media", comment, media.map(e -> 1).orElse(0));
        if (comment.getId() != null) {
            throw new BadRequestAlertException("A new comment cannot already have an ID", ENTITY_NAME, "idexists");
        }
        comment = commentService.save(comment, media);
        return ResponseEntity.created(new URI("/api/comments/" + comment.getId()))
                .headers(HeaderUtil.createEntityCreationAlert(applicationName, ENTITY_NAME,
                        comment.getId().toString()))
                .body(comment);
    }

    /**
     * {@code PUT  /comments} : Updates an existing comment.
     *
     * @param comment the comment to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body
     *         the updated comment,
     *         or with status {@code 400 (Bad Request)} if the comment is not valid,
     *         or with status {@code 500 (Internal Server Error)} if the comment
     *         couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PutMapping("")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Comment> updateComment(
            @RequestPart Comment comment,
            @RequestPart(required = false) Optional<File> deletedFile,
            @RequestPart(required = false) Optional<MultipartFile> media) throws URISyntaxException {
        log.debug("REST request to update Comment : {} + {}x media, and {}x to be deleted", comment,
                media.map(e -> 1).orElse(0), deletedFile.map(e -> 1).orElse(0));

        if (comment.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }

        comment = commentService.update(comment, media, deletedFile);
        return ResponseEntity.ok()
                .headers(HeaderUtil.createEntityUpdateAlert(applicationName, ENTITY_NAME,
                        comment.getId().toString()))
                .body(comment);
    }

    /**
     * {@code GET  /comments/channel/:id} : get all the comments for a given channel
     * id.
     *
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list
     *         of comments in body.
     */
    @GetMapping("/channel/{id}")
    public ResponseEntity<PageDTO<CommentDTO>> getCommentsByChannel(@PathVariable Long id, Pageable pageable) {
        log.debug("REST request to get Comments of Channel id : {} | Pageable {}", id, pageable);
        if (id == null) {
            throw new BadRequestAlertException("Channel id is required to retrieve comments", ENTITY_NAME,
                    "idChannelNull");
        }
        Slice<CommentDTO> page = commentRepository.findCommentsByChannelOrByAtDesc(id, pageable)
                .map(c -> CommentDTO.from(c));
        return ResponseEntity.ok().body(PageDTO.from(page));
    }

    /**
     * {@code GET  /comments/post/:id} : get all the comments for a given post id.
     *
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list
     *         of comments in body.
     */
    @GetMapping("/post/{id}")
    public ResponseEntity<PageDTO<CommentDTO>> getCommentsByPost(@PathVariable Long id, Pageable pageable) {
        log.debug("REST request to get Comments of Post id : {} | Pageable {}", id, pageable);
        if (id == null) {
            throw new BadRequestAlertException("Post id is required to retrieve comments", ENTITY_NAME,
                    "idPostNull");
        }
        Slice<CommentDTO> page = commentRepository.findTopLevelCommentsByPost(id, pageable)
                .map(c -> CommentDTO.from(c));
        return ResponseEntity.ok().body(PageDTO.from(page));
    }

    @GetMapping("/reply/{id}")
    public ResponseEntity<List<CommentDTO>> getRepliesByComment(@PathVariable Long id, Pageable pageable) {
        log.debug("REST request to get Comments of Post id : {} | Pageable {}", id, pageable);
        List<CommentDTO> commentDTOs = commentRepository.findRepliesByComment(id)
                .stream().map(c -> CommentDTO.from(c)).toList();
        return ResponseEntity.ok().body(commentDTOs);
    }

    /**
     * {@code DELETE  /comments/:id} : delete the "id" comment.
     *
     * @param id the id of the comment to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> deleteComment(@PathVariable Long id) {
        log.debug("REST request to delete Comment : {}", id);
        commentService.delete(id);
        return ResponseEntity.noContent()
                .headers(HeaderUtil.createEntityDeletionAlert(applicationName, ENTITY_NAME, id.toString()))
                .build();
    }
}
