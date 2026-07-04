package org.ngelmakproject.web.rest;

import java.util.List;

import org.ngelmakproject.domain.File;
import org.ngelmakproject.service.FileService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/files")
public class FileController {

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    /**
     * Upload one or many media files.
     * Postman:
     * Body → form-data → key: medias (type: File) → select multiple files
     */
    @PostMapping
    public ResponseEntity<List<File>> uploadMedias(
            @RequestPart("medias") List<MultipartFile> medias) {
        log.debug("Received request to upload {} media files", medias.size());
        List<File> saved = fileService.save(medias);
        return ResponseEntity.ok(saved);
    }

    /**
     * Upload media files + optional cover files.
     * Postman:
     * medias → File (multiple)
     * covers → File (multiple, optional)
     */
    @PostMapping("/with-covers")
    public ResponseEntity<List<File>> uploadMediasWithCovers(
            @RequestPart("medias") List<MultipartFile> medias,
            @RequestPart("covers") List<MultipartFile> covers) {

        List<File> saved = fileService.save(medias, covers);
        return ResponseEntity.ok(saved);
    }

    /**
     * Delete files by URL.
     * Postman:
     * Body → raw → JSON:
     * ["http://your-host/public/file1.jpg", "http://your-host/public/file2.jpg"]
     */
    @DeleteMapping
    public ResponseEntity<Void> deleteByUrls(@RequestBody List<String> urls) {
        fileService.deleteByUrls(urls);
        return ResponseEntity.noContent().build();
    }

    /**
     * Simple test endpoint.
     */
    @GetMapping("/test")
    public String test() {
        return "File API is running";
    }
}
