package org.ngelmakproject.service.storage;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Service for uploading and managing files in SeaweedFS distributed storage.
 * Handles single/batch uploads and deletions.
 */
@Slf4j
@Service
public class SeaweedFsService {

    @Value("${seaweedfs.filer.url}")
    private String filerUrl;

    private final WebClient webClient;

    public SeaweedFsService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    /**
     * Upload a single file to SeaweedFS Filer.
     * 
     * @param file The file to upload
     * @param path The path in the filer where the file should be stored (e.g.,
     *             /public/filename.jpg)
     * @return A Mono containing the URL of the uploaded file
     */
    public Mono<String> uploadFile(MultipartFile file, String path) {
        log.debug("Uploading file {} to SeaweedFS at path {}", file.getOriginalFilename(), path);
        return webClient
                .post()
                .uri(filerUrl + path)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData("file", file.getResource()))
                .retrieve()
                .bodyToMono(String.class)
                .thenReturn(filerUrl + path); // Return the full URL of the uploaded file
    }

    /**
     * Upload multiple files to SeaweedFS Filer.
     * 
     * @param files     Array of files to upload
     * @param filenames Array of filenames corresponding to the files (must be same
     *                  length as files array)
     * @param basePath  The base path in the filer where files should be stored
     *                  (e.g.,
     *                  /public)
     * @return A Mono containing a list of URLs of the uploaded files
     */
    public Mono<List<String>> uploadFiles(MultipartFile[] files, String[] filenames, String basePath) {
        log.debug("Uploading {} files to SeaweedFS at base path {}", files.length, basePath);
        return Flux.range(0, files.length)
                .flatMap(i -> {
                    MultipartFile file = files[i];
                    String filePath = basePath + "/" + filenames[i];
                    return uploadFile(file, filePath);
                })
                .collectList();
    }

    /**
     * Delete a single file from SeaweedFS Filer.
     * 
     * @param internalUrl The internal URL of the file to delete (e.g.,
     *                    http://storage.ngelmak.org/path/to/file.jpg)
     * @return A Mono that completes when the deletion is done
     */
    public Mono<Void> deleteFile(String internalUrl) {
        log.debug("Deleting file at internal URL {}", internalUrl);
        return webClient
                .delete()
                .uri(internalUrl)
                .retrieve()
                .toBodilessEntity()
                .then();
    }

    /**
     * Delete multiple files from SeaweedFS Filer.
     * 
     * @param internalUrls List of internal URLs of files to delete
     * @return A Mono that completes when all deletions are done
     */
    public Mono<Void> deleteFiles(List<String> internalUrls) {
        log.debug("Deleting {} files from SeaweedFS", internalUrls.size());
        return Flux.fromIterable(internalUrls)
                .flatMap(this::deleteFile)
                .then();
    }
}
