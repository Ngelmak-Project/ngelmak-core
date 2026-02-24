package org.ngelmakproject.web.rest;

import java.net.URISyntaxException;
import java.util.Optional;

import org.ngelmakproject.domain.Ticket;
import org.ngelmakproject.service.TicketService;
import org.ngelmakproject.web.rest.dto.PageDTO;
import org.ngelmakproject.web.rest.dto.TicketDTO;
import org.ngelmakproject.web.rest.errors.BadRequestAlertException;
import org.ngelmakproject.web.rest.errors.UnauthorizedResourceAccessException;
import org.ngelmakproject.web.rest.util.HeaderUtil;
import org.ngelmakproject.web.rest.util.ResponseUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * REST controller for managing {@link org.ngelmakproject.domain.Ticket}.
 */
@RestController
@RequestMapping("/api/tickets")
public class TicketResource {

    private static final Logger log = LoggerFactory.getLogger(TicketResource.class);

    private static final String ENTITY_NAME = "ticket";

    @Value("${spring.application.name}")
    private String applicationName;

    private final TicketService ticketService;

    public TicketResource(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    /**
     * {@code POST  /tickets} : Create a new ticket.
     *
     * @param ticket the ticket to create.
     * @param media  evidence of the issue.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with
     *         body the new ticket, or with status {@code 400 (Bad Request)} if the
     *         ticket has already an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PostMapping("")
    public ResponseEntity<TicketDTO> createTicket(@RequestPart(name = "ticket", required = true) Ticket ticket,
            @RequestPart(name = "media", required = false) Optional<MultipartFile> media) {
        log.info("REST request to save Ticket : {} + {}x media", ticket, media.map(e -> 1).orElse(0));
        if (ticket.getId() != null) {
            throw new BadRequestAlertException("A new ticket cannot already have an ID", ENTITY_NAME, "idexists");
        }
        ticket = ticketService.report(ticket, media);
        return ResponseEntity.ok()
                .body(TicketDTO.from(ticket));
    }

    /**
     * {@code GET  /tickets} : get all the tickets.
     *
     * @param pageable the pagination information.
     * @throws UnauthorizedResourceAccessException
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list
     *         of tickets in body.
     */
    @GetMapping("")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<PageDTO<Ticket>> getAllTickets(Pageable pageable) {
        log.info("REST request to get a page of Tickets");
        Page<Ticket> page = ticketService.findAll(pageable);
        return ResponseEntity.ok().body(PageDTO.from(page));
    }

    /**
     * {@code GET  /tickets/user-activity-report} : get all the tickets.
     *
     * @param pageable the pagination information.
     * @throws UnauthorizedResourceAccessException
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list
     *         of tickets in body.
     */
    @GetMapping("/user-activity-report")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PageDTO<Ticket>> userActivityReports(Pageable pageable) {
        log.info("REST request to get a page of Tickets that concern the current user activity");
        Page<Ticket> page = ticketService.findAllUserActivityReports(pageable);
        return ResponseEntity.ok().body(PageDTO.from(page));
    }

    /**
     * {@code GET  /tickets/:id} : get the "id" ticket.
     *
     * @param id the id of the ticket to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body
     *         the ticket, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Ticket> getTicket(@PathVariable("id") Long id) {
        log.debug("REST request to get Ticket : {}", id);
        Optional<Ticket> ticket = ticketService.findOne(id);
        return ResponseUtil.wrapOrNotFound(ticket);
    }

    /**
     * {@code DELETE  /tickets/:id} : delete the "id" ticket.
     *
     * @param id the id of the ticket to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTicket(@PathVariable("id") Long id) {
        log.debug("REST request to delete Ticket : {}", id);
        ticketService.delete(id);
        return ResponseEntity.noContent()
                .headers(HeaderUtil.createEntityDeletionAlert(applicationName, ENTITY_NAME, id.toString()))
                .build();
    }
}
