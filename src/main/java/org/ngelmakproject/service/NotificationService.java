package org.ngelmakproject.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.ngelmakproject.domain.Notification;
import org.ngelmakproject.repository.NotificationRepository;
import org.ngelmakproject.web.rest.dto.NotificationDTO;
import org.ngelmakproject.web.rest.errors.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class NotificationService {
    private static final String ENTITY_NAME = "notification";
    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    /**
     * Create a new scheduled notification.
     * If scheduledAt is null, default to now.
     */
    public Notification schedule(NotificationDTO notificationDTO) {
        log.debug("Request to schedule a new Notification : {}", notificationDTO);
        Notification notification = new Notification();
        notification.setContent(notificationDTO.content());
        notification.setType(notificationDTO.type());
        if (notificationDTO.scheduledAt() == null) {
            notification.setScheduledAt(Instant.now());
        } else {
            notification.setScheduledAt(notificationDTO.scheduledAt());
        }
        notification.setExpiresAt(
                notificationDTO.scheduledAt().plus(notificationDTO.expiresAfterHours(), ChronoUnit.HOURS));
        return notificationRepository.save(notification);
    }

    /**
     * Update a notification.
     *
     * @param notification the entity to save.
     * @return the persisted entity.
     */
    public Notification update(NotificationDTO notificationDTO) {
        log.debug("Request to update Notification : {}", notificationDTO);
        return notificationRepository
                .findById(notificationDTO.id())
                .map(existingNotification -> {
                    if (notificationDTO.type() != null) {
                        existingNotification.setType(notificationDTO.type());
                    }
                    if (notificationDTO.content() != null) {
                        existingNotification.setContent(notificationDTO.content());
                    }
                    if (notificationDTO.scheduledAt() != null) {
                        existingNotification.setScheduledAt(notificationDTO.scheduledAt());
                        existingNotification.setExpiresAt(
                                notificationDTO.scheduledAt().plus(notificationDTO.expiresAfterHours(),
                                        ChronoUnit.HOURS));
                    }
                    if (notificationDTO.expiresAfterHours() != null) {
                        existingNotification.setExpiresAt(
                                notificationDTO.scheduledAt().plus(notificationDTO.expiresAfterHours(),
                                        ChronoUnit.HOURS));
                    }
                    return existingNotification;
                })
                .map(notificationRepository::save)
                .orElseThrow(() -> new ResourceNotFoundException("Entity not found", ENTITY_NAME, "idnotfound"));
    }

    /**
     * Returns notifications that are currently active.
     * Active = now is between scheduledAt and scheduledAt + expiresAfter.
     */
    @Transactional(readOnly = true)
    public List<Notification> getTop10ActiveNotifications() {
        log.debug("Request to get top 10 active Notifications");
        return notificationRepository.findActiveRandom(Instant.now(), PageRequest.of(0, 10));
    }
}