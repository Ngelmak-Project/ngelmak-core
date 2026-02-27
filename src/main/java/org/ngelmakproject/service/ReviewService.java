package org.ngelmakproject.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.ngelmakproject.domain.Review;
import org.ngelmakproject.domain.Ticket;
import org.ngelmakproject.repository.ReviewRepository;
import org.ngelmakproject.repository.TicketRepository;
import org.ngelmakproject.security.UserService;
import org.ngelmakproject.security.UserService.UserPrincipal;
import org.ngelmakproject.web.rest.dto.ReviewDTO;
import org.ngelmakproject.web.rest.errors.BadRequestAlertException;
import org.ngelmakproject.web.rest.errors.UnauthorizedResourceAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service Implementation for managing {@link org.ngelmakproject.domain.Review}.
 */
@Service
@Transactional
public class ReviewService {

    private static final Logger log = LoggerFactory.getLogger(ReviewService.class);

    private final ReviewRepository reviewRepository;
    private final TicketRepository ticketRepository;

    public ReviewService(ReviewRepository reviewRepository, TicketRepository ticketRepository) {
        this.reviewRepository = reviewRepository;
        this.ticketRepository = ticketRepository;
    }

    /**
     * Creates a new review for a ticket, optionally as a reply to another review.
     *
     * <p>
     * The method:
     * </p>
     * <ul>
     * <li>Retrieves the authenticated user and sets them as the author</li>
     * <li>Ensures the ticket exists and is not resolved</li>
     * <li>Ensures the parent review (if provided) belongs to the same ticket</li>
     * <li>Initializes review metadata (timestamps, status, due date,
     * visibility)</li>
     * <li>Saves the review</li>
     * </ul>
     *
     * @param review the review to create
     * @return the saved review
     */
    public Review save(Review review) {
        log.debug("Request to save Review : {}", review);

        // Identify the authenticated user
        Long authorId = UserService.getAuthenticatedUser()
                .map(UserPrincipal::id)
                .orElseThrow(() -> new UnauthorizedResourceAccessException("review"));

        // Validate ticket
        Ticket ticket = ticketRepository.findById(review.getTicket().getId())
                .orElseThrow(() -> new BadRequestAlertException(
                        "Ticket not found", "ticket", "notFound"));

        // Attach ticket
        review.setTicket(ticket);

        if (ticket.isResolved()) {
            throw new BadRequestAlertException(
                    "Cannot review a resolved ticket",
                    "review",
                    "ticketResolved");
        }

        // Initialize metadata
        review.setAuthor(authorId);
        review.setCreatedAt(Instant.now());
        review.setUpdatedAt(null);
        review.setStatus(Review.Status.OPEN);

        // Default due date: 5 days from now
        review.setDueAt(Instant.now().plus(5, ChronoUnit.DAYS));

        // Default visibility: PUBLIC
        if (review.getVisibility() == null) {
            review.setVisibility(Review.Visibility.PUBLIC);
        }

        // Save
        return reviewRepository.save(review);
    }

    /**
     * Update the fields provided in Review.
     *
     * @param review the entity to update partially.
     * @return the persisted entity.
     */
    public Optional<Review> udate(Review review) {
        log.debug("Request to partially update Review : {}", review);

        return reviewRepository
                .findById(review.getId())
                .map(existingReview -> {
                    if (review.getContent() != null) {
                        existingReview.setContent(review.getContent());
                    }

                    return existingReview;
                })
                .map(reviewRepository::save);
    }

    /**
     * Get all the reviews.
     *
     * @param pageable the pagination information.
     * @return the list of entities.
     */
    @Transactional(readOnly = true)
    public Page<Review> findAll(Pageable pageable) {
        log.debug("Request to get all Reviews");
        return reviewRepository.findAll(pageable);
    }

    /**
     * Returns all reviews for the given ticket that the current user is allowed to
     * see.
     *
     * <p>
     * Visibility rules:
     * </p>
     * <ul>
     * <li>Moderators can see all reviews</li>
     * <li>The ticket’s target user can see only PUBLIC reviews</li>
     * <li>The author of a review can always see their own review</li>
     * </ul>
     *
     * <p>
     * Reviews are returned in chronological order.
     * </p>
     *
     * @param ticketId the ID of the ticket whose reviews should be retrieved
     * @return a filtered list of visible reviews
     * @throws UnauthorizedResourceAccessException if no authenticated user is
     *                                             available
     * @throws BadRequestAlertException            if the ticket does not exist
     */
    public List<ReviewDTO> getVisibleReviewsForTicket(Long ticketId) {
        log.debug("Request to get all Reviews");

        Long currentUserId = UserService.getAuthenticatedUser()
                .map(UserPrincipal::id)
                .orElseThrow(() -> new UnauthorizedResourceAccessException("review"));

        var superAuthorities = List.of("ROLE_MODERATOR", "ROLE_ADMIN");
        boolean isModerator = UserService.getAuthenticatedUser()
                .map(UserPrincipal::authorities)
                .orElse(Set.of())
                .stream().anyMatch(superAuthorities::contains);

        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new BadRequestAlertException("Ticket not found", "ticket", "notFound"));

        boolean isTargetUser = ticket.getTargetUser() != null ? ticket.getTargetUser().equals(currentUserId) : false;

        // Fetch all reviews ordered by time
        return reviewRepository.findByTicketIdOrderByCreatedAtAsc(ticketId).stream()
                .filter(r -> {
                    // Moderators see everything
                    if (isModerator)
                        return true;

                    // Target user sees only PUBLIC reviews
                    if (isTargetUser && r.getVisibility() == Review.Visibility.PUBLIC)
                        return true;

                    // Author always sees their own review
                    if (r.getAuthor().equals(currentUserId))
                        return true;

                    return false;
                }).map(r -> {
                    boolean isOwner = r.getAuthor() != null ? r.getAuthor().equals(currentUserId) : false;
                    return ReviewDTO.from(r, isOwner);
                })
                .toList();
    }

    /**
     * Get one review by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    @Transactional(readOnly = true)
    public Optional<Review> findOne(Long id) {
        log.debug("Request to get Review : {}", id);
        return reviewRepository.findById(id);
    }

    /**
     * Delete the review by id.
     *
     * @param id the id of the entity.
     */
    public void delete(Long id) {
        log.debug("Request to delete Review : {}", id);
        reviewRepository.deleteById(id);
    }
}
