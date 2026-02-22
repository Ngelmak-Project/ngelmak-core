package org.ngelmakproject.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.ngelmakproject.domain.File;
import org.ngelmakproject.domain.Ticket;
import org.ngelmakproject.repository.TicketRepository;
import org.ngelmakproject.security.UserService;
import org.ngelmakproject.security.UserService.UserPrincipal;
import org.ngelmakproject.web.rest.errors.UnauthorizedResourceAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Service Implementation for managing {@link org.ngelmakproject.domain.Ticket}.
 */
@Service
@Transactional
public class TicketService {

    private static final Logger log = LoggerFactory.getLogger(TicketService.class);
    private static final String ENTITY_NAME = "ticket";

    private final TicketRepository ticketRepository;
    private final FileService fileService;

    public TicketService(TicketRepository ticketRepository, FileService fileService) {
        this.ticketRepository = ticketRepository;
        this.fileService = fileService;
    }

    /**
     * Creates and saves a new {@link Ticket} reported by the currently
     * authenticated user.
     *
     * <p>
     * The method performs the following steps:
     * </p>
     * <ul>
     * <li>Identifies the authenticated user and sets them as the issuer of the
     * ticket</li>
     * <li>Stores the optional media file (if provided) and attaches it as
     * evidence</li>
     * <li>Resolves the target user based on the associated channel, post, or
     * comment</li>
     * <li>Initializes ticket metadata such as creation timestamp and resolved
     * status</li>
     * <li>Saves the ticket to the repository</li>
     * </ul>
     *
     * <p>
     * <strong>TODO:</strong> Assign the ticket to a moderator user (e.g., randomly
     * or based on a load-balancing strategy).
     * </p>
     *
     * @param ticket the ticket being reported
     * @param media  an optional media file to attach as evidence
     * @return the persisted {@link Ticket}
     * @throws UnauthorizedResourceAccessException if no authenticated user is
     *                                             available
     */

    public Ticket report(Ticket ticket, Optional<MultipartFile> media) {
        log.debug("Request to save Ticket : {}", ticket);

        // 1. Identify issuer
        Long issuedBy = UserService.getAuthenticatedUser()
                .map(UserPrincipal::id)
                .orElseThrow(() -> new UnauthorizedResourceAccessException(ENTITY_NAME));
        ticket.setIssuedBy(issuedBy);

        // 2. Save evidence if provided
        media.ifPresent(m -> {
            File saved = fileService.save(List.of(m)).get(0);
            ticket.setEvidence(saved);
        });

        // 3. Resolve target user
        Long channelId = ticket.getChannel() != null ? ticket.getChannel().getId() : null;
        Long postId = ticket.getPost() != null ? ticket.getPost().getId() : null;
        Long commentId = ticket.getComment() != null ? ticket.getComment().getId() : null;

        Long targetUser = ticketRepository
                .getUserIdByChannelOrPostOrComment(channelId, postId, commentId)
                .orElse(null);

        ticket.setTargetUser(targetUser);

        // 4. Metadata
        ticket.setIssuedAt(Instant.now());
        ticket.setResolved(false);

        // 5. Save
        return ticketRepository.save(ticket);
    }

    /**
     * Get all the tickets.
     *
     * @param pageable the pagination information.
     * @return the list of entities.
     */
    @Transactional(readOnly = true)
    public Page<Ticket> findAll(Pageable pageable) {
        log.debug("Request to get all Tickets");
        return ticketRepository.findAll(pageable);
    }

    /**
     * Get one ticket by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    @Transactional(readOnly = true)
    public Optional<Ticket> findOne(Long id) {
        log.debug("Request to get Ticket : {}", id);
        return ticketRepository.findById(id);
    }

    /**
     * Delete the ticket by id.
     *
     * @param id the id of the entity.
     */
    public void delete(Long id) {
        log.debug("Request to delete Ticket : {}", id);
        ticketRepository.deleteById(id);
    }
}
