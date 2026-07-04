package org.ngelmakproject.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.ngelmakproject.config.Constants;
import org.ngelmakproject.domain.Comment;
import org.ngelmakproject.domain.File;
import org.ngelmakproject.repository.CommentRepository;
import org.ngelmakproject.repository.projection.CommentProjection;
import org.ngelmakproject.service.cache.CommentRedisService;
import org.ngelmakproject.web.rest.dto.CommentDTO;
import org.ngelmakproject.web.rest.errors.BadRequestAlertException;
import org.ngelmakproject.web.rest.errors.ChannelNotFoundException;
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
public class CommentService {
	private static final String ENTITY_NAME = "comment";
	private static final Logger log = LoggerFactory.getLogger(CommentService.class);

	private final FileService fileService;
	private final ChannelService channelService;
	private final CommentRepository commentRepository;

	private final CommentRedisService commentRedisService;

	public CommentService(CommentRepository commentRepository, FileService fileService,
			ChannelService channelService, PostService postService,
			CommentRedisService commentRedisService) {
		this.commentRepository = commentRepository;
		this.fileService = fileService;
		this.channelService = channelService;
		this.commentRedisService = commentRedisService;
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
	@Transactional(readOnly = false) // must be transactional to save media and update counters
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
		if (comment.getPost() == null && comment.getReplyTo() == null) {
			throw new BadRequestAlertException(
					"A comment must refer to either a Post or another Comment.",
					ENTITY_NAME,
					"missingPostOrComment");
		}
		// Save to Redis
		commentRedisService.queueCreate(comment);
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
	@Transactional(readOnly = false) // must be transactional to save media and update the comment
	public Comment update(Comment comment, Optional<MultipartFile> media, Optional<File> deletedFile) {
		log.debug("Request to update Comment : {} | {} file(s)",
				comment, media.isPresent() ? 1 : 0);
		// Validate content
		validateCommentContent(comment.getContent());

		var channel = channelService.findOneByCurrentUser()
				.orElseThrow(ChannelNotFoundException::new);
		Comment existing = commentRepository.findById(comment.getId())
				.orElseThrow(
						() -> new ResourceNotFoundException("Entity not found", ENTITY_NAME,
								"idnotfound"));

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
			deletedFile.ifPresent(file -> fileService.deleteByIds(List.of(file.getId())));
			existing.setFile(newFiles.stream().findFirst().orElse(null));
		}
		// Save to Redis
		commentRedisService.queueUpdate(existing);
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
			if (!channel.getId().equals(projection.getChannel().getId())) {
				throw new UnauthorizedResourceAccessException(
						channel.getUser(), id, ENTITY_NAME);
			}

			// No pending CREATE found → queue a DELETE operation
			commentRedisService.queueDelete(projection);
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

	/**
	 * <p>
	 * Retrieves the list of replies for a given comment.
	 *
	 * This method fetches all replies to a specific comment. If the number of
	 * replies exceeds the count stored on the client, it queues an update to
	 * refresh the reply count in Redis.
	 * </p>
	 *
	 * @param id               the identifier of the parent comment
	 * @param storedReplyCount the number of replies currently stored on the client
	 * @return a list of CommentDTOs representing the replies
	 */
	public List<CommentDTO> findRepliesByComment(long id, int storedReplyCount) {
		List<CommentDTO> commentDTOs = commentRepository.findRepliesByComment(id)
				.stream().map(c -> CommentDTO.from(c)).toList();
		if (storedReplyCount > 0 && commentDTOs.size() != storedReplyCount) {
			log.warn(
					"Reply count mismatch for comment id {}: client has {}, actual is {}. Queuing count update.",
					id, storedReplyCount, commentDTOs.size());
			commentRedisService.queueReplyCount(id);
		}
		return commentDTOs;
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
	@Transactional(readOnly = false)
	@Scheduled(cron = "0 0 3 * * *") // every day at 3 AM
	public void purgeDeletedComments() {
		Instant cutoff = Instant.now().minus(7, ChronoUnit.DAYS);

		List<CommentProjection> comments = commentRepository.findExpiredComments(cutoff);

		if (comments.isEmpty()) {
			return;
		}

		// Extract file IDs
		List<Long> fileIds = comments.stream()
				.map(c -> c.getFile().getId())
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

		log.debug("Purged {} comments and {} files older than {}",
				commentIds.size(), fileIds.size(), cutoff);
	}
}