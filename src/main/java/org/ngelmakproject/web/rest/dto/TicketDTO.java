package org.ngelmakproject.web.rest.dto;

import java.time.Instant;

import org.ngelmakproject.domain.File;
import org.ngelmakproject.domain.Ticket;

public record TicketDTO(
		Long id,
		Instant issuedAt,
		boolean resolved,
		String description,
		File evidence,
		PostDTO post,
		CommentDTO comment,
		ChannelDTO channel,
		Long targetUser,
		Long issuedBy,
		Long handledBy,
		Long assignedTo) {
	public static TicketDTO from(Ticket ticket) {
		if (ticket == null)
			return null;
		return new TicketDTO(
				ticket.getId(),
				ticket.getIssuedAt(),
				ticket.isResolved(),
				ticket.getDescription(),
				ticket.getEvidence(),
				PostDTO.from(ticket.getPost(), null),
				CommentDTO.from(ticket.getComment()),
				ChannelDTO.from(ticket.getChannel()),
				ticket.getTargetUser(),
				ticket.getIssuedBy(),
				ticket.getHandledBy(),
				ticket.getAssignedTo());
	}
}
