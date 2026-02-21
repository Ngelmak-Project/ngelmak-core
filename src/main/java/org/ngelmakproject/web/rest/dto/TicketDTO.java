package org.ngelmakproject.web.rest.dto;

import java.time.Instant;

import org.ngelmakproject.domain.File;
import org.ngelmakproject.domain.Ticket;

public record TicketDTO(
		Long id,
		Instant issuedAt,
		Boolean resolved,
		String description,
		File evidence,
		PostDTO post,
		CommentDTO comment,
		ChannelDTO channel,
		Long issuedBy,
		Long handledBy,
		Long assignedTo) {
	public static TicketDTO from(Ticket ticket) {
		return new TicketDTO(
				ticket.getId(),
				ticket.getIssuedAt(),
				ticket.getResolved(),
				ticket.getDescription(),
				ticket.getEvidence(),
				PostDTO.from(ticket.getPost(), null),
				CommentDTO.from(ticket.getComment()),
				ChannelDTO.from(ticket.getChannel()),
				ticket.getIssuedBy(),
				ticket.getHandledBy(),
				ticket.getAssignedTo());
	}
}
