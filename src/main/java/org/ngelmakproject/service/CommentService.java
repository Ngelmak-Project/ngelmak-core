package org.ngelmakproject.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.ngelmakproject.domain.Comment;
import org.ngelmakproject.domain.File;
import org.ngelmakproject.repository.CommentRepository;
import org.ngelmakproject.repository.projection.CommentProjection;
import org.ngelmakproject.web.rest.errors.ChannelNotFoundException;
import org.ngelmakproject.web.rest.errors.BadRequestAlertException;
import org.ngelmakproject.web.rest.errors.ResourceNotFoundException;
import org.ngelmakproject.web.rest.errors.UnauthorizedResourceAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Service Implementation for managing
 * {@link org.ngelmakproject.domain.Comment}.
 */
@Service
@Transactional
public class CommentService {

    private static final String ENTITY_NAME = "comment";
    private static final Logger log = LoggerFactory.getLogger(CommentService.class);

    private final FileService fileService;
    private final PostService postService;
    private final ChannelService channelService;
    private final CommentRepository commentRepository;

    public CommentService(CommentRepository commentRepository, FileService fileService,
            ChannelService channelService, PostService postService) {
        this.commentRepository = commentRepository;
        this.fileService = fileService;
        this.channelService = channelService;
        this.postService = postService;
    }

    /**
     * Save a comment.
     *
     * @param comment the entity to save.
     * @return the persisted entity.
     */
    public Comment save(Comment comment, Optional<MultipartFile> media) {
        log.debug("Request to save Comment : {} | {}x file", comment, media.map(e -> 1).orElse(0));
        if (comment.getContent().length() > 1000) {
            throw new BadRequestAlertException("Contenu trop long > 1000 caractères.", ENTITY_NAME, "contentTooLong");
        }
        // [TODO] This action should be done asynchronously with redis database
        return channelService.findOneByCurrentUser().map(channel -> {
            /* 1. we start by saving the files if exists */
            List<MultipartFile> medias = media.map(m -> Arrays.asList(m)).orElse(List.of());
            List<File> files = fileService.save(medias);
            /* 2. then save the Comment with the attachment */
            comment
                    .at(Instant.now()) // set the current time
                    .file(files.stream().findFirst().orElse(null)) // attach the file is exists.
                    .channel(channel); // set the current connected user as owner of the comment.
            // [TODO] Use Redis to record the changes.
            if (comment.getPost() != null) {
                this.postService.updateCommmentCount(comment.getPost().getId(), 1);
            } else if (comment.getReplyTo() != null) {
                this.updateReplyCount(comment.getReplyTo().getId(), 1);
            } else {
                throw new BadRequestAlertException(
                        "A comment must always refer to at least one Post or Comment, but none have been provided.",
                        ENTITY_NAME, "missingPostOrComment");
            }

            commentRepository.save(comment);
            return comment;
        }).orElseThrow(ChannelNotFoundException::new);
    }

    /**
     * Update a comment.
     *
     * @param comment the entity to update partially.
     * @return the persisted entity.
     */
    public Comment update(Comment comment, Optional<MultipartFile> media, Optional<File> deletedFile) {
        log.debug("Request to save Comment : {} | {}x file", comment, media.map(e -> 1).orElse(0));
        if (comment.getContent().length() > 1000) {
            throw new BadRequestAlertException("Contenu trop long > 1000 caractères.", ENTITY_NAME, "contentTooLong");
        }
        return channelService.findOneByCurrentUser().map(channel -> {
            return commentRepository
                    .findById(comment.getId())
                    .map(existingComment -> {
                        if (channel.getId() != existingComment.getChannel().getId()) {
                            throw new UnauthorizedResourceAccessException(channel.getUser(), existingComment.getId(),
                                    ENTITY_NAME);
                        }
                        existingComment.setLastUpdate(Instant.now());
                        if (comment.getContent() != null) {
                            existingComment.setContent(comment.getContent());
                        }
                        if (media.isPresent()) {
                            /* 1. we start by saving the files if exists */
                            List<MultipartFile> medias = media.map(m -> Arrays.asList(m)).orElse(List.of());
                            List<File> files = fileService.save(medias);
                            // 2. attach the file is exists.
                            if (deletedFile.isPresent()) {
                                this.fileService.delete(Arrays.asList(deletedFile.get()));
                            }
                            existingComment.setFile(files.stream().findFirst().orElse(null));
                        }
                        return this.commentRepository.save(existingComment);
                    })
                    .orElseThrow(() -> new ResourceNotFoundException("Entity not found", ENTITY_NAME, "idnotfound"));
        }).orElseThrow(ChannelNotFoundException::new);
    }

    /**
     * Update reply comments.
     * 
     * <p>
     * This method is responsible of tracking and updating total replies on a
     * comment.
     * <\p>
     * 
     * [TODO] This method later should consider reading from Redis database and
     * update automatically the comment count.
     * It should be handle by a cron
     * 
     * @param commentId
     * @param count     could be a positive or negative number.
     */
    // @Scheduled(cron = "0 0 2 * * *") // Run daily at 2 AM
    public void updateReplyCount(Long commentId, Integer count) {
        this.commentRepository.updateReplyCount(commentId, count);
    }

    /**
     * Soft‑deletes a comment owned by the current authenticated user.
     *
     * <p>
     * This method performs an authorization check using a lightweight projection
     * to avoid loading the full entity. If the comment belongs to the current user,
     * it is soft‑deleted using a JPQL update (no entity loading, no dirty
     * checking).
     * After deletion, the method updates either the parent post's comment count or
     * the parent comment's reply count, depending on the comment type.
     * </p>
     *
     * <p>
     * File deletion and permanent cleanup are intentionally deferred to a
     * scheduled cron job to avoid unnecessary I/O during user‑initiated deletes.
     * </p>
     *
     * @param id the identifier of the comment to delete
     * @throws ChannelNotFoundException            if no authenticated channel is
     *                                             found
     * @throws UnauthorizedResourceAccessException if the comment does not belong to
     *                                             the current user
     */
    public void delete(Long id) {
        log.debug("Request to delete Comment : {}", id);

        var channel = channelService.findOneByCurrentUser()
                .orElseThrow(ChannelNotFoundException::new);

        commentRepository.findProjectedById(id).ifPresent(projection -> {

            // Authorization check: ensure the comment belongs to the current user
            if (!channel.getId().equals(projection.getChannelId())) {
                throw new UnauthorizedResourceAccessException(
                        channel.getUser(), id, ENTITY_NAME);
            }

            // Soft delete using JPQL update (no entity loading)
            commentRepository.softDeleteById(id, Instant.now());

            // TODO: Record deletion event in Redis for async processing
            // Update counters depending on comment type
            if (projection.getPostId() != null) {
                postService.updateCommmentCount(projection.getPostId(), -1);
            } else if (projection.getReplyToId() != null) {
                updateReplyCount(projection.getReplyToId(), -1);
            }
            // else: root comment with no parent — nothing to update
        });
    }

    /**
     * Permanently deletes comments that were soft‑deleted more than 30 days ago.
     *
     * <p>
     * This scheduled task performs a two‑phase cleanup:
     * <ul>
     * <li>Fetch expired comments using a lightweight projection (ID + fileId)</li>
     * <li>Delete associated files in batch</li>
     * <li>Hard‑delete the comments using a bulk delete</li>
     * </ul>
     *
     * <p>
     * No entities are loaded during this process. All operations rely on
     * projections and batch operations for maximum efficiency.
     * </p>
     */
    @Scheduled(cron = "0 0 3 * * *") // every day at 3 AM
    @Transactional
    public void purgeDeletedComments() {
        Instant cutoff = Instant.now().minus(30, ChronoUnit.DAYS);

        List<CommentProjection> comments = commentRepository.findExpiredComments(cutoff);

        if (comments.isEmpty()) {
            return;
        }

        // Extract file IDs
        List<Long> fileIds = comments.stream()
                .map(CommentProjection::getFileId)
                .filter(Objects::nonNull)
                .toList();

        if (!fileIds.isEmpty()) {
            fileService.deleteByIds(fileIds);
        }

        // Extract comment IDs
        List<Long> commentIds = comments.stream()
                .map(CommentProjection::getId)
                .toList();

        // Hard delete comments
        commentRepository.deleteAllByIdInBatch(commentIds);

        log.info("Purged {} comments and {} files older than {}",
                commentIds.size(), fileIds.size(), cutoff);
    }

}