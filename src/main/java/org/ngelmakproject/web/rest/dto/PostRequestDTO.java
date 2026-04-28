package org.ngelmakproject.web.rest.dto;

import java.util.List;

import org.ngelmakproject.domain.Post;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for organizing post creation/update parameters.
 * Encapsulates the post entity and associated file resources (both multipart
 * files and URLs).
 *
 * @param post           the post entity to create or update (required)
 * @param medias         media files to attach to the post via multipart upload
 *                       (optional, defaults to empty list)
 * @param mediaUrls      URLs of external media resources to attach to the post
 *                       (optional, defaults to empty list)
 * @param covers         cover/thumbnail files via multipart upload (optional,
 *                       defaults to empty list)
 * @param coverUrls      URLs of external cover/thumbnail resources (optional,
 *                       defaults to empty list)
 * @param deletedFileIds file IDs or resource IDs to delete during update
 *                       operations (optional, defaults to empty list)
 */
public record PostRequestDTO(
        @NotNull(message = "Post cannot be null") Post post,

        @RequestPart(required = false) List<MultipartFile> medias,

        @RequestPart(required = false) List<String> mediaUrls,

        @RequestPart(required = false) List<MultipartFile> covers,

        @RequestPart(required = false) List<String> coverUrls,

        @RequestPart(required = false) List<String> deletedFileIds) {

    /**
     * Compact constructor to apply defaults for optional file and URL lists.
     */
    public PostRequestDTO {
        medias = medias != null ? medias : List.of();
        mediaUrls = mediaUrls != null ? mediaUrls : List.of();
        covers = covers != null ? covers : List.of();
        coverUrls = coverUrls != null ? coverUrls : List.of();
        deletedFileIds = deletedFileIds != null ? deletedFileIds : List.of();
    }
}
