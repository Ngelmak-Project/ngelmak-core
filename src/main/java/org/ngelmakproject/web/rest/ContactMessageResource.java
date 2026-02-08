package org.ngelmakproject.web.rest;

import java.net.URI;
import java.net.URISyntaxException;

import org.ngelmakproject.domain.ContactMessage;
import org.ngelmakproject.service.ContactMessageService;
import org.ngelmakproject.web.rest.dto.PageDTO;
import org.ngelmakproject.web.rest.errors.BadRequestAlertException;
import org.ngelmakproject.web.rest.util.HeaderUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

/**
 * REST controller for managing
 * {@link org.ngelmakproject.domain.ContactMessage}.
 */
@RestController
@RequestMapping("/api/contact-messages")
public class ContactMessageResource {

    private static final Logger log = LoggerFactory.getLogger(ContactMessageResource.class);

    private static final String ENTITY_NAME = "contantMessage";

    @Value("${spring.application.name}")
    private String applicationName;

    private final ContactMessageService contantMessageService;

    public ContactMessageResource(ContactMessageService contantMessageService) {
        this.contantMessageService = contantMessageService;
    }

    /**
     * {@code POST  /contact-messages} : Create a new contantMessage.
     *
     * @param contantMessage the contantMessage to create.
     * @return the {@link ResponseEntity} with status {@code 201 (Created)} and with
     *         body the new contantMessage, or with status {@code 400 (Bad Request)}
     *         if
     *         the contantMessage has already an ID.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PostMapping("")
    public ResponseEntity<ContactMessage> createContactMessage(@Valid @RequestBody ContactMessage contantMessage)
            throws URISyntaxException {
        log.debug("REST request to save ContactMessage : {}", contantMessage);
        if (contantMessage.getId() != null) {
            throw new BadRequestAlertException("A new contantMessage cannot already have an ID", ENTITY_NAME,
                    "idexists");
        }
        contantMessage = contantMessageService.save(contantMessage);
        return ResponseEntity.created(new URI("/api/contact-messages/" + contantMessage.getId()))
                .headers(HeaderUtil.createEntityCreationAlert(applicationName, ENTITY_NAME,
                        contantMessage.getId().toString()))
                .body(contantMessage);
    }

    /**
     * {@code PUT  /contact-messages/:id} : Updates an existing contantMessage.
     *
     * @param id             the id of the contantMessage to save.
     * @param contantMessage the contantMessage to update.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body
     *         the updated contantMessage,
     *         or with status {@code 400 (Bad Request)} if the contantMessage is not
     *         valid,
     *         or with status {@code 500 (Internal Server Error)} if the
     *         contantMessage
     *         couldn't be updated.
     * @throws URISyntaxException if the Location URI syntax is incorrect.
     */
    @PutMapping("")
    public ResponseEntity<ContactMessage> updateContactMessage(
            @Valid @RequestBody ContactMessage contantMessage) throws URISyntaxException {
        log.debug("REST request to update ContactMessage : {}", contantMessage);
        if (contantMessage.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        contantMessage = contantMessageService.update(contantMessage);
        return ResponseEntity.ok()
                .headers(
                        HeaderUtil.createEntityUpdateAlert(applicationName, ENTITY_NAME,
                                contantMessage.getId().toString()))
                .body(contantMessage);
    }

    /**
     * {@code GET  /contact-messages} : get all the contact-messages.
     *
     * @param pageable the pagination information.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list
     *         of contact-messages in body.
     */
    @GetMapping("")
    public ResponseEntity<PageDTO<ContactMessage>> getAllContactMessages(Pageable pageable) {
        log.debug("REST request to get a page of ContactMessages");
        PageDTO<ContactMessage> page = contantMessageService.findAllUntreatedContactMessage(pageable);
        return ResponseEntity.ok().body(page);
    }

    /**
     * {@code DELETE  /contact-messages/:id} : delete the "id" contantMessage.
     *
     * @param id the id of the contantMessage to delete.
     * @return the {@link ResponseEntity} with status {@code 204 (NO_CONTENT)}.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteContactMessage(@PathVariable Long id) {
        log.debug("REST request to delete ContactMessage : {}", id);
        contantMessageService.delete(id);
        return ResponseEntity.noContent()
                .build();
    }
}
