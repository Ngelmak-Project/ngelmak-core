package org.ngelmakproject.web.rest;

import java.net.URISyntaxException;
import java.util.List;

import org.ngelmakproject.domain.Review;
import org.ngelmakproject.service.ReviewService;
import org.ngelmakproject.web.rest.dto.ReviewDTO;
import org.ngelmakproject.web.rest.errors.BadRequestAlertException;
import org.ngelmakproject.web.rest.util.ResponseUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for managing {@link org.ngelmakproject.domain.Review}.
 */
@RestController
@RequestMapping("/api/v1/reviews")
public class ReviewResource {

    private static final Logger log = LoggerFactory.getLogger(ReviewResource.class);

    private static final String ENTITY_NAME = "review";

    @Value("${spring.application.name}")
    private String applicationName;

    private final ReviewService reviewService;

    public ReviewResource(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    /**
     * {@code POST  /reviews} : Create a new review.
     *
     * @param review the review to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with
     *         body the new review, or with status {@code 400 (Bad Request)} if the
     *         review has already an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PostMapping("")
    public ResponseEntity<ReviewDTO> createReview(@RequestBody Review review) {
        log.debug("REST request to save Review : {}", review);
        if (review.getId() != null) {
            throw new BadRequestAlertException("A new review cannot already have an ID", ENTITY_NAME, "idexists");
        }
        review = reviewService.save(review);
        return ResponseEntity.ok().body(ReviewDTO.from(review));
    }

    /**
     * {@code PUT  /reviews} : Update an existing review.
     *
     * @param review the review to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with
     *         body the updated review, or with status {@code 400 (Bad Request)}
     *         if the review does not have an ID or doesn't exist,
     *         or with status {@code 404 (Not Found)} if the review can't be found.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PutMapping("")
    public ResponseEntity<ReviewDTO> updateReview(@RequestBody Review review) {
        log.debug("REST request to update Review: {}", review);

        if (review.getId() == null) {
            throw new BadRequestAlertException("Invalid review ID", ENTITY_NAME, "idnull");
        }

        return ResponseUtil.wrapOrNotFound(reviewService.update(review).map(ReviewDTO::from));
    }

    /**
     * {@code GET  /reviews} : get all the reviews.
     *
     * @param pageable the pagination information.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list
     *         of reviews in body.
     */
    @GetMapping("/{ticketId}")
    public ResponseEntity<List<ReviewDTO>> getReviews(@PathVariable Long ticketId) {
        log.debug("REST request to get All Reviews");
        List<ReviewDTO> reviews = reviewService.getVisibleReviewsForTicket(ticketId);
        return ResponseEntity.ok(reviews);
    }

    /**
     * {@code DELETE  /reviews/:id} : delete the "id" review.
     *
     * @param id the id of the review to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteReview(@PathVariable("id") Long id) {
        log.debug("REST request to delete Review : {}", id);
        reviewService.delete(id);
        return ResponseEntity.noContent()
                .build();
    }
}
