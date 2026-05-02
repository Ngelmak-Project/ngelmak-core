package org.ngelmakproject.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.ngelmakproject.config.Constants;
import org.ngelmakproject.domain.Comment;
import org.ngelmakproject.domain.File;
import org.ngelmakproject.domain.Reaction;
import org.ngelmakproject.repository.CommentRepository;
import org.ngelmakproject.repository.projection.CommentProjection;
import org.ngelmakproject.service.operation.Operation;
import org.ngelmakproject.web.rest.errors.BadRequestAlertException;
import org.ngelmakproject.web.rest.errors.ChannelNotFoundException;
import org.ngelmakproject.web.rest.errors.ResourceNotFoundException;
import org.ngelmakproject.web.rest.errors.UnauthorizedResourceAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Service Implementation for managing
 * {@link org.ngelmakproject.domain.Comment}.
 */
@Service
public class CommentService {

    private static final String ENTITY_NAME = "comment";
    private static final String REDIS_PENDING_KEY = "comment:pending";
    private static final String REDIS_PENDING_REPLY_COUNT_KEY = "comment:pending:replycount";
    private static final Logger log = LoggerFactory.getLogger(CommentService.class);

    private record ReplyCountDTO(long id, int count) {
    }

    private final FileService fileService;
    private final PostService postService;
    private final ChannelService channelService;
    private final CommentRepository commentRepository;
    private final RedisTemplate<String, String> redisTemplate;

    public CommentService(CommentRepository commentRepository, FileService fileService,
            ChannelService channelService, PostService postService,
            RedisTemplate<String, String> redisTemplate) {
        this.commentRepository = commentRepository;
        this.fileService = fileService;
        this.channelService = channelService;
        this.postService = postService;
        this.redisTemplate = redisTemplate;
    }

    /**
     * Creates and persists a new Comment.
     *
     * <p>
     * This method performs the following steps:
     * </p>
     * <ol>
     * <li>Validates the comment content (non-empty, within allowed length).</li>
     * <li>Retrieves the current user's channel.</li>
     * <li>Saves the optional media file, if provided.</li>
     * <li>Populates metadata on the Comment (timestamp, file, channel).</li>
     * <li>Updates the parent Post or Comment counters.</li>
     * <li>Persists the Comment entity.</li>
     * </ol>
     *
     * @param comment the Comment entity to create
     * @param media   an optional media file attached to the comment
     * @return the persisted Comment entity
     * @throws BadRequestAlertException if content is invalid or no parent is
     *                                  provided
     * @throws ChannelNotFoundException if the current user has no associated
     *                                  channel
     */
    @Transactional(readOnly = true)
    public Comment save(Comment comment, Optional<MultipartFile> media) {
        log.debug("Request to save Comment : {} | {} file(s)",
                comment, media.isPresent() ? 1 : 0);
        // Validate content
        validateCommentContent(comment.getContent());

        var channel = channelService.findOneByCurrentUser()
                .orElseThrow(ChannelNotFoundException::new);
        // Save media (if provided)
        List<MultipartFile> mediaList = media
                .map(List::of)
                .orElse(List.of());
        List<File> savedFiles = fileService.save(mediaList);
        // Prepare the comment entity
        comment.at(Instant.now())
                .file(savedFiles.stream().findFirst().orElse(null))
                .channel(channel);
        // Update counters (post or parent comment)
        if (comment.getPost() != null) {
            postService.updateCommmentCount(comment.getPost().getId(), 1);
        } else if (comment.getReplyTo() != null) {
            // Record to redis for updating reply count.
            Operation<ReplyCountDTO> defaultOp = Operation
                    .defaultOperation(new ReplyCountDTO(comment.getReplyTo().getId(), 1));
            redisTemplate.opsForHash()
                    .put(REDIS_PENDING_REPLY_COUNT_KEY, defaultOp.id(), defaultOp.toJson());
        } else {
            throw new BadRequestAlertException(
                    "A comment must refer to either a Post or another Comment.",
                    ENTITY_NAME,
                    "missingPostOrComment");
        }
        // Save to Redis
        Operation<Comment> op = Operation.createOperation(comment);
        comment.setId(op.idAsLong()); // Set comment ID from operation
        redisTemplate.opsForHash()
                .put(REDIS_PENDING_KEY, op.id(), op.toJson());
        log.info("Saved Post creation to Redis - data: {}", op.toJson());
        return comment; // return immediately
    }

    /**
     * Updates an existing Comment.
     *
     * <p>
     * This method performs the following steps:
     * </p>
     * <ol>
     * <li>Validates the updated content.</li>
     * <li>Retrieves the current user's channel.</li>
     * <li>Loads the existing Comment and checks ownership.</li>
     * <li>Updates content and timestamp.</li>
     * <li>Handles media replacement (save new file, delete old one).</li>
     * <li>Persists the updated Comment.</li>
     * </ol>
     *
     * @param comment     the Comment entity containing updated fields
     * @param media       an optional new media file to attach
     * @param deletedFile an optional file to delete if replaced
     * @return the updated Comment entity
     * @throws UnauthorizedResourceAccessException if the user does not own the
     *                                             comment
     * @throws ResourceNotFoundException           if the comment does not exist
     * @throws ChannelNotFoundException            if the current user has no
     *                                             associated channel
     */
    @Transactional(readOnly = true)
    public Comment update(Comment comment, Optional<MultipartFile> media, Optional<File> deletedFile) {
        log.debug("Request to update Comment : {} | {} file(s)",
                comment, media.isPresent() ? 1 : 0);
        // Validate content
        validateCommentContent(comment.getContent());

        var channel = channelService.findOneByCurrentUser()
                .orElseThrow(ChannelNotFoundException::new);
        Comment existing = commentRepository.findById(comment.getId())
                .orElseThrow(
                        () -> new ResourceNotFoundException("Entity not found", ENTITY_NAME, "idnotfound"));

        // If the key is found then remove.
        removeRedisById(existing.getId());

        // Ownership check
        if (!channel.getId().equals(existing.getChannel().getId())) {
            throw new UnauthorizedResourceAccessException(
                    channel.getUser(), existing.getId(), ENTITY_NAME);
        }
        // Update fields
        existing.setLastUpdate(Instant.now());
        existing.setContent(comment.getContent());
        // Handle media update
        if (media.isPresent()) {
            List<File> newFiles = fileService.save(List.of(media.get()));
            deletedFile.ifPresent(file -> fileService.delete(List.of(file)));
            existing.setFile(newFiles.stream().findFirst().orElse(null));
        }
        // Save to Redis
        Operation<Comment> op = Operation.updateOperation(existing.getId(), existing);
        redisTemplate.opsForHash()
                .put(REDIS_PENDING_KEY, op.id(), op.toJson());

        return existing;
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
    @Transactional(readOnly = true)
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

            // No pending CREATE found → queue a DELETE operation
            Operation<Long> deleteOp = Operation.deleteOperation(id);
            redisTemplate.opsForHash()
                    .put(REDIS_PENDING_KEY, deleteOp.id(), deleteOp.toJson());

            // Update counters depending on comment type
            if (projection.getPostId() != null) {
                postService.updateCommmentCount(projection.getPostId(), -1);
            } else if (projection.getReplyToId() != null) {
                // Record to redis for updating reply count.
                Operation<ReplyCountDTO> defaultOp = Operation
                        .defaultOperation(new ReplyCountDTO(projection.getReplyToId(), -1));
                redisTemplate.opsForHash()
                        .put(REDIS_PENDING_REPLY_COUNT_KEY, defaultOp.id(), defaultOp.toJson());
            }
        });
    }

    /**
     * Validate comment content for creation or update.
     * 
     * @param content the content to validate
     * @throws BadRequestAlertException if the content is null, blank, or exceeds
     *                                  the maximum allowed length.
     */
    private void validateCommentContent(String content) {
        if (content == null || content.isBlank()) {
            throw new BadRequestAlertException(
                    "Content cannot be empty.",
                    ENTITY_NAME,
                    "contentEmpty");
        }
        if (content.length() > Constants.MAX_COMMENT_LENGTH) {
            throw new BadRequestAlertException(
                    "Content too long (> " + Constants.MAX_COMMENT_LENGTH + " characters).",
                    ENTITY_NAME,
                    "contentTooLong");
        }
    }

    private boolean removeRedisById(long id) {
        // If the key is found then remove.
        Map<Object, Object> pendingOps = redisTemplate.opsForHash().entries(REDIS_PENDING_KEY);
        for (Map.Entry<Object, Object> entry : pendingOps.entrySet()) {
            Operation<Reaction> op = Operation.fromJson((String) entry.getValue());
            if (op.idAsLong() == id) {
                redisTemplate.opsForHash().delete(REDIS_PENDING_KEY, entry.getKey());
                log.warn("Cancelled pending CREATE/UDATE for reaction {}", op.id());
                return true;
            }
        }
        return false;
    }

    /**
     * Periodically flushes aggregated reply count updates from Redis to the
     * database.
     * 
     * <p>
     * Runs every 2 minutes, retrieving pending operations from Redis, aggregating
     * count changes by comment ID, and applying batch updates to the database.
     * </p>
     */
    @Scheduled(fixedDelay = 2, timeUnit = TimeUnit.MINUTES)
    public void flushReplyCount() {
        Map<Object, Object> entries = redisTemplate.opsForHash()
                .entries(REDIS_PENDING_REPLY_COUNT_KEY);

        if (entries.isEmpty()) {
            return;
        }

        log.info("Flushing {} pending reply count operations", entries.size());

        // Aggregate and apply updates in one operation
        entries.values().stream()
                .map(json -> Operation.<ReplyCountDTO>fromJson(json).data())
                .collect(Collectors.toMap(
                        ReplyCountDTO::id,
                        ReplyCountDTO::count,
                        Integer::sum))
                .forEach(commentRepository::updateReplyCount);

        // Clear processed entries
        redisTemplate.opsForHash().delete(REDIS_PENDING_REPLY_COUNT_KEY, entries.keySet());
    }

    /**
     * Flushes pending Comment operations from Redis to the database.
     * Processes CREATE, UPDATE, and DELETE operations in batches.
     * Scheduled every 2 minutes.
     */
    @Transactional
    @Scheduled(fixedDelay = 2, timeUnit = TimeUnit.MINUTES)
    public void flushPendingComments() {
        Map<Object, Object> entries = redisTemplate.opsForHash()
                .entries(REDIS_PENDING_KEY);

        if (entries.isEmpty()) {
            return;
        }

        log.info("Flushing {} pending reaction operations", entries.size());

        List<Comment> toSave = new ArrayList<>();
        List<Long> toDelete = new ArrayList<>();
        List<Object> processedKeys = new ArrayList<>();
        for (Map.Entry<Object, Object> entry : entries.entrySet()) {
            Object key = entry.getKey();
            String json = (String) entry.getValue();
            Operation<Comment> op = Operation.fromJson(json);
            switch (op.type()) {
                case CREATE -> {
                    op.data().setId(null); // Clear ID for new comments
                    toSave.add(op.data());
                    processedKeys.add(key);
                }
                case UPDATE -> {
                    toSave.add(op.data());
                    processedKeys.add(key);
                }
                case DELETE -> {
                    toDelete.add(op.data().getId());
                    processedKeys.add(key);
                }
                default -> {
                }
            }
        }

        if (!toSave.isEmpty()) {
            // Save or update
            commentRepository.saveAll(toSave);
            log.info("Saved/updated {} reactions", toSave.size());
        }

        if (!toDelete.isEmpty()) {
            commentRepository.softDeleteByIds(toDelete, Instant.now());
            log.info("Deleted {} reactions", toDelete.size());
        }

        if (!processedKeys.isEmpty()) {
            redisTemplate.opsForHash().delete(REDIS_PENDING_KEY, processedKeys.toArray());
            log.info("Removed {} processed operations from Redis", processedKeys.size());
        }
    }

    /**
     * Permanently deletes comments that were soft‑deleted more than 7 days ago.
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
    @Transactional
    @Scheduled(cron = "0 0 3 * * *") // every day at 3 AM
    public void purgeDeletedComments() {
        Instant cutoff = Instant.now().minus(7, ChronoUnit.DAYS);

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