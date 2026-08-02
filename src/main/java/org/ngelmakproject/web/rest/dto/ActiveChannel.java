package org.ngelmakproject.web.rest.dto;

public record ActiveChannel(
		Long id,
		String name,
		String identifier,
		String avatar,
		String banner,
		String description,
		Long postCount) {
}
