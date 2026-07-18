package org.ngelmakproject.web.rest;

import org.ngelmakproject.domain.Reaction;
import org.ngelmakproject.service.ReactionService;
import org.ngelmakproject.web.rest.errors.BadRequestAlertException;
import org.ngelmakproject.web.rest.util.HeaderUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

/**
 * REST controller for managing {@link org.ngelmakproject.domain.Reaction}.
 */
@RestController
@RequestMapping("/api/v1/reactions")
public class ReactionResource {

    private static final Logger log = LoggerFactory.getLogger(ReactionResource.class);

    private static final String ENTITY_NAME = "reaction";

    @Value("${spring.application.name}")
    private String applicationName;

    private final ReactionService reactionService;

    public ReactionResource(ReactionService reactionService) {
        this.reactionService = reactionService;
    }

    /**
     * {@code POST  /reactions} : Create a new reaction.
     *
     * @param reaction the reaction to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with
     *         body the new reaction, or with status {@code 400 (Bad Request)} if
     *         the
     *         reaction has already an ID.
     */
    @PostMapping("")
    public ResponseEntity<Reaction> createReaction(@Valid @RequestBody Reaction reaction) {
        log.debug("REST request to save Reaction : {}", reaction);
        if (reaction.getId() != null) {
            throw new BadRequestAlertException("A new reaction cannot already have an ID", ENTITY_NAME, "idexists");
        }
        reaction = reactionService.save(reaction);
        return ResponseEntity.ok().body(reaction);
    }

    /**
     * {@code PUT  /reactions/:id} : Updates an existing reaction.
     *
     * @param id       the id of the reaction to save.
     * @param reaction the reaction to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body
     *         the updated reaction,
     *         or with status {@code 400 (Bad Request)} if the reaction is not
     *         valid,
     *         or with status {@code 500 (Internal Server Error)} if the reaction
     *         couldn't be updated.
     */
    @PutMapping("")
    public ResponseEntity<Reaction> updateReaction(
            @Valid @RequestBody Reaction reaction) {
        log.debug("REST request to update Reaction : {}", reaction);
        if (reaction.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        reaction = reactionService.update(reaction);
        return ResponseEntity.ok().body(reaction);
    }

    /**
     * {@code DELETE  /reactions/:id} : delete the "id" reaction.
     *
     * @param id the id of the reaction to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteReaction(@PathVariable Long id) {
        log.debug("REST request to delete Reaction : {}", id);
        reactionService.delete(id);
        return ResponseEntity.noContent()
                .headers(HeaderUtil.createEntityDeletionAlert(applicationName, ENTITY_NAME, id.toString()))
                .build();
    }
}
