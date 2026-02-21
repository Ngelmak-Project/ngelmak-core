package org.ngelmakproject.service;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.ngelmakproject.domain.File;
import org.ngelmakproject.domain.Ticket;
import org.ngelmakproject.repository.TicketRepository;
import org.ngelmakproject.security.UserService;
import org.ngelmakproject.security.UserService.UserPrincipal;
import org.ngelmakproject.web.rest.dto.TicketDTO;
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
     * Save a ticket.
     *
     * @param ticket the entity to save.
     * @return the persisted entity.
     */
    public Ticket save(Ticket ticket, Optional<MultipartFile> media) {
        log.debug("Request to save Ticket : {}", ticket);
        /* 1. Set issuer of the ticket by using curring authenticated user. */
        Long issuedBy = UserService.getAuthenticatedUser().map(UserPrincipal::id)
                .orElseThrow(() -> new UnauthorizedResourceAccessException(ENTITY_NAME));
        ticket.setIssuedBy(issuedBy);
        /* [TODO] Randomly assing the ticket to any User with MODERATOR Authority */
        /* 1. we start by saving the files if exists */
        List<MultipartFile> medias = media.map(m -> Arrays.asList(m)).orElse(List.of());
        List<File> files = fileService.save(medias);
        if (!files.isEmpty()) {
            ticket.setEvidence(files.get(0));
        }
        ticket.setIssuedAt(Instant.now());
        ticket.setResolved(false);
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
    public Optional<TicketDTO> findOne(Long id) {
        log.debug("Request to get Ticket : {}", id);
        return ticketRepository.findById(id).map(TicketDTO::from);
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
