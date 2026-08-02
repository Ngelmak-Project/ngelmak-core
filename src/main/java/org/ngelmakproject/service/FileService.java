package org.ngelmakproject.service;

import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.ngelmakproject.domain.File;
import org.ngelmakproject.repository.FileRepository;
import org.ngelmakproject.repository.projection.FileProjection;
import org.ngelmakproject.service.cache.FileRedisService;
import org.ngelmakproject.service.storage.SeaweedFsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Service Implementation for managing
 * {@link org.ngelmakproject.domain.File}.
 */
@Service
@Transactional
public class FileService {

    private static final Logger log = LoggerFactory.getLogger(FileService.class);

    @Value("${file.public.base-url}")
    private String publicBaseUrl;

    private final FileRepository fileRepository;
    private final FileRedisService fileRedisService;
    private final SeaweedFsService seaweedFsService;

    public FileService(FileRepository fileRepository,
            FileRedisService fileRedisService,
            SeaweedFsService seaweedFsService) {
        this.fileRepository = fileRepository;
        this.fileRedisService = fileRedisService;
        this.seaweedFsService = seaweedFsService;
    }

    /**
     * Saves uploaded media files without covers, with deduplication.
     * 
     * @param medias List of media files (required)
     * @return List of saved File entities (media only)
     */
    public List<File> save(List<MultipartFile> medias) {
        log.debug("Request to save {}x file(s)", medias.size());
        return save(medias, Collections.nCopies(medias.size(), null));
    }

    /**
     * Saves uploaded media files with their corresponding covers, with
     * deduplication.
     * 
     * @param medias List of media files (required)
     * @param covers List of cover files (optional, can contain nulls)
     * @return List of saved File entities (media + covers)
     */
    public List<File> save(List<MultipartFile> medias, List<MultipartFile> covers) {
        log.debug("Request to save {}x file(s) and {}x cover(s)", medias.size(), covers.size());
        if (medias.isEmpty()) {
            return List.of();
        }

        Instant now = Instant.now();

        // Build list of all parts (media + cover)
        List<MultipartFile> allParts = new ArrayList<>();
        List<File> prepared = new ArrayList<>();

        for (int i = 0; i < medias.size(); i++) {
            MultipartFile mediaPart = medias.get(i);
            File media = fromMultipartToFile(mediaPart);
            prepared.add(media);
            allParts.add(mediaPart);

            MultipartFile coverPart = covers.get(i);
            if (coverPart != null && !coverPart.isEmpty()) {
                File cover = fromMultipartToFile(coverPart);
                media.setCover(cover);
                prepared.add(cover);
                allParts.add(coverPart);
            }
        }

        // Load existing files by hash
        Set<String> hashes = prepared.stream()
                .map(File::getHash)
                .collect(Collectors.toSet());

        Map<String, File> existing = fileRepository.findByHashIn(hashes)
                .stream()
                .collect(Collectors.toMap(File::getHash, f -> f));

        // Filter new files
        List<File> newFiles = prepared.stream()
                .filter(f -> !existing.containsKey(f.getHash()))
                .toList();

        // Upload new files in batch using your uploadFiles()
        MultipartFile[] newParts = newFiles.stream()
                .map(f -> {
                    // find matching multipart
                    return allParts.stream()
                            .filter(p -> computeHash(p).equals(f.getHash()))
                            .findFirst()
                            .orElseThrow();
                })
                .toArray(MultipartFile[]::new);
        String[] paths = newFiles.stream()
                .map(File::getFilename)
                .toArray(String[]::new);

        List<String> internalUrls = seaweedFsService.uploadFiles(newParts, paths, "/public")
                .block(); // only blocking point

        // Assign URLs and save
        for (int i = 0; i < newFiles.size(); i++) {
            File f = newFiles.get(i);
            String internalUrl = internalUrls.get(i);

            String path = "/public/" + f.getFilename();
            f.setInternalUrl(internalUrl);
            f.setUrl(publicBaseUrl + path);
            f.setCreatedAt(now);

            fileRepository.save(f);
        }

        // Combine new + existing
        List<File> result = new ArrayList<>(newFiles);
        result.addAll(existing.values());

        // Increment usage count
        result.stream().map(File::getId).forEach(id -> fileRedisService.queueUsageCount(id, 1));

        return result;
    }

    /**
     * Saves files from given URLs with deduplication. Used for external media
     * (e.g. YouTube thumbnails).
     * 
     * @param mediaUrls List of media URLs (required)
     * @param coverUrls List of cover URLs (optional, can contain nulls)
     * @return List of saved File entities (media + covers)
     */
    public List<File> saveFromUrls(List<String> mediaUrls, List<String> coverUrls) {
        log.debug("Request to save {}x file(s) and {}x cover(s)", mediaUrls.size(), coverUrls.size());
        if (mediaUrls.isEmpty()) {
            return List.of();
        }

        List<File> prepared = new ArrayList<>();
        Map<String, String> hashToUrl = new HashMap<>();

        for (int i = 0; i < mediaUrls.size(); i++) {
            // MEDIA
            String mediaUrl = mediaUrls.get(i);
            File media = fromUrlToFile(mediaUrl);
            prepared.add(media);
            hashToUrl.put(media.getHash(), mediaUrl);

            // COVER
            String coverUrl = coverUrls.get(i);
            if (coverUrl != null && !coverUrl.isBlank()) {
                File cover = fromUrlToFile(coverUrl);
                media.setCover(cover);
                prepared.add(cover);
                hashToUrl.put(cover.getHash(), coverUrl);
            }
        }

        // Load existing files by hash
        Map<String, File> existing = fileRepository.findByHashIn(hashToUrl.keySet())
                .stream()
                .collect(Collectors.toMap(File::getHash, f -> f));

        // Persist only new files
        List<File> newFiles = prepared.stream()
                .filter(f -> !existing.containsKey(f.getHash()))
                .map(f -> {
                    f.setUrl(hashToUrl.get(f.getHash()));
                    f.setCreatedAt(Instant.now());
                    return f;
                })
                .map(fileRepository::save)
                .toList();

        List<File> result = new ArrayList<>(newFiles);
        result.addAll(existing.values());

        result.stream().map(File::getId).forEach(id -> fileRedisService.queueUsageCount(id, 1));

        return result;
    }

    /**
     * Deletes files by their URLs.
     *
     * @param urls List of file URLs to delete
     */
    @Transactional(readOnly = true)
    public void deleteByUrls(List<String> urls) {
        log.debug("Request to delete files with URLs: {}", urls);
        urls.forEach(url -> fileRedisService.queueUsageCount(url, -1));
    }

    /**
     * Decrements usage count for given file IDs. Actual deletion happens in
     * cleanup schedule when usageCount reaches 0.
     *
     * @param fileIds List of file IDs to mark for deletion
     */
    @Transactional(readOnly = true)
    public void deleteByIds(List<Long> fileIds) {
        log.debug("Request to delete files with IDs: {}", fileIds);
        fileIds.forEach(id -> fileRedisService.queueUsageCount(id, -1));
    }

    /**
     * Cleanup unused files: delete from SeaweedFS + DB.
     */
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void cleanupUnusedFiles() {
        log.warn("Launching the cleanup schedule for unused files");

        List<FileProjection> unusedFiles = fileRepository.findUnusedFiles();
        if (unusedFiles.isEmpty()) {
            return;
        }

        // Delete from SeaweedFS
        seaweedFsService.deleteFiles(unusedFiles.stream().map(FileProjection::internalUrl).toList())
                .block(); // Blocking for simplicity; can be optimized with async if needed

        // Delete DB entries
        fileRepository.deleteUnusedFiles(
                unusedFiles.stream().map(FileProjection::id).toList());
    }

    /**
     * Converts a multipart file to a File entity.
     *
     * @param media The multipart file to convert
     * @return The converted File entity
     */
    private File fromMultipartToFile(MultipartFile media) {
        String name = media.getOriginalFilename();
        String ext = (name != null && name.contains(".")) ? name.substring(name.lastIndexOf('.') + 1).toLowerCase()
                : "";

        File file = new File();
        String hash = computeHash(media);

        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String shortHash = hash.substring(0, 8);
        String filename = String.format("nk_%s_%s.%s", date, shortHash, ext);

        file.setHash(hash);
        file.setFilename(filename);
        file.setType(media.getContentType());
        file.setSize(media.getSize());
        return file;
    }

    /**
     * Converts a URL to a File entity. Used for external media (e.g. YouTube
     * thumbnails).
     *
     * @param url The URL to convert
     * @return The converted File entity
     */
    private File fromUrlToFile(String url) {
        String filename = url.replaceAll(".*/", "").split("[?#]")[0];
        File file = new File();
        file.setFilename(filename);
        file.setUrl(url);
        file.setHash(url);
        file.setSize(0L);
        return file;
    }

    /**
     * Computes a SHA-256 hash of the file content for deduplication.
     *
     * @param file The multipart file to hash
     * @return The computed hash as a hex string
     */
    private String computeHash(MultipartFile file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(file.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes)
                sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("Unable to compute hash", e);
        }
    }
}
