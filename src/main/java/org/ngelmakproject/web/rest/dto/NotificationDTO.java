package org.ngelmakproject.web.rest.dto;

import java.time.Instant;

import org.ngelmakproject.domain.enumeration.NotificationType;

public record NotificationDTO(
		Long id,
		String content,
		NotificationType type,
		Instant scheduledAt,
		Integer expiresAfterHours) {
}
