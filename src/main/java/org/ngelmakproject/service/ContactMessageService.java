package org.ngelmakproject.service;

import org.ngelmakproject.domain.ContactMessage;
import org.ngelmakproject.repository.ContactMessageRepository;
import org.ngelmakproject.web.rest.dto.PageDTO;
import org.ngelmakproject.web.rest.errors.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service Implementation for managing
 * {@link org.ngelmakproject.domain.ContactMessage}.
 */
@Service
@Transactional
public class ContactMessageService {

    private static final String ENTITY_NAME = "contactMessage";
    private static final Logger log = LoggerFactory.getLogger(ContactMessageService.class);

    private final ContactMessageRepository contactMessageRepository;

    public ContactMessageService(ContactMessageRepository contactMessageRepository) {
        this.contactMessageRepository = contactMessageRepository;
    }

    /**
     * Save a contactMessage.
     *
     * @param contactMessage the entity to save.
     * @return the persisted entity.
     */
    public ContactMessage save(ContactMessage contactMessage) {
        log.debug("Request to save ContactMessage : {}", contactMessage);
        return contactMessageRepository.save(contactMessage);
    }

    /**
     * Update a contactMessage.
     *
     * @param contactMessage the entity to save.
     * @return the persisted entity.
     */
    public ContactMessage update(ContactMessage contactMessage) {
        log.debug("Request to update ContactMessage : {}", contactMessage);
        return contactMessageRepository
                .findById(contactMessage.getId())
                .map(existingContactMessage -> {
                    if (contactMessage.getEmail() != null) {
                        existingContactMessage.setEmail(contactMessage.getEmail());
                    }
                    if (contactMessage.getSubject() != null) {
                        existingContactMessage.setSubject(contactMessage.getSubject());
                    }
                    if (contactMessage.getMessage() != null) {
                        existingContactMessage.setMessage(contactMessage.getMessage());
                    }
                    if (contactMessage.getMessage() != null) {
                        existingContactMessage.setMessage(contactMessage.getMessage());
                    }
                    if (contactMessage.getStatus() != null) {
                        existingContactMessage.setStatus(contactMessage.getStatus());
                    }
                    return existingContactMessage;
                })
                .map(contactMessageRepository::save)
                .orElseThrow(() -> new ResourceNotFoundException("Entity not found", ENTITY_NAME, "idnotfound"));
    }

    /**
     * Get all the contactMessages.
     *
     * @param pageable the pagination information.
     * @return the list of entities.
     */
    @Transactional(readOnly = true)
    public PageDTO<ContactMessage> findAllUntreatedContactMessage(Pageable pageable) {
        log.debug("Request to get all ContactMessages");
        var page = contactMessageRepository.findUnclosedContactMessageOrderByCreatedAt(pageable);
        return PageDTO.from(page);
    }

    /**
     * Delete the contactMessage by id.
     *
     * @param id the id of the entity.
     */
    public void delete(Long id) {
        log.debug("Request to delete ContactMessage : {}", id);
        contactMessageRepository.deleteById(id);
    }
}
