package org.ngelmakproject.service;

import java.net.URL;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.ngelmakproject.domain.File;
import org.ngelmakproject.repository.FileRepository;
import org.ngelmakproject.repository.projection.FileProjection;
import org.ngelmakproject.service.storage.FileStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private final FileRepository fileRepository;
    private final FileStorageService fileStorageService;

    public FileService(FileRepository fileRepository,
            FileStorageService fileStorageService) {
        this.fileRepository = fileRepository;
        this.fileStorageService = fileStorageService;
    }

    /**
     * Saves uploaded media files with deduplication.
     *
     * <p>
     * Computes a hash for each upload.
     * Reuses existing File entries when hashes match.
     * Stores only new files on disk and persists them.
     * Increments usageCount for every returned file.
     * <\p>
     *
     * @param medias uploaded multipart files
     * @return list of File entities (existing + newly saved)
     */
    public List<File> save(List<MultipartFile> medias) {
        log.debug("Request to save {}x file(s)", medias.size());
        return save(medias, Collections.nCopies(medias.size(), null));
    }

    /**
     * Saves media files and their optional covers with deduplication.
     *
     * <p>
     * Computes a hash for each media and cover.
     * Reuses existing File entries when hashes match.
     * Stores only new files on disk and persists them.
     * Links each media to its cover when provided.
     * Batch‑increments usageCount for all returned files.
     * <\p>
     *
     * @param medias list of media files
     * @param covers list of cover files (same size as medias, may contain
     *               null/empty entries)
     * @return list of File entities (existing + newly saved)
     */
    public List<File> save(List<MultipartFile> medias, List<MultipartFile> covers) {
        log.debug("Request to save {}x file(s) and {}x cover(s)", medias.size(), covers.size());
        if (medias.isEmpty()) {
            return List.of();
        }

        List<File> prepared = new ArrayList<>();
        Map<String, MultipartFile> hashToMultipart = new HashMap<>();

        for (int i = 0; i < medias.size(); i++) {
            // MEDIA
            MultipartFile mediaPart = medias.get(i);
            File media = fromMultipartToFile(mediaPart);
            prepared.add(media);
            hashToMultipart.put(media.getHash(), mediaPart);

            // COVER (optional)
            MultipartFile coverPart = covers.get(i);
            if (coverPart != null && !coverPart.isEmpty()) {
                File cover = fromMultipartToFile(coverPart);
                media.setCover(cover);
                prepared.add(cover);
                hashToMultipart.put(cover.getHash(), coverPart);
            }
        }

        // Load existing files by hash
        Map<String, File> existing = fileRepository.findByHashIn(hashToMultipart.keySet())
                .stream()
                .collect(Collectors.toMap(File::getHash, f -> f));

        // Persist only new files
        List<File> newFiles = prepared.stream()
                .filter(f -> !existing.containsKey(f.getHash()))
                .map(f -> {
                    URL url = fileStorageService.store(hashToMultipart.get(f.getHash()), true, f.getFilename());
                    f.setUrl(url.toString());
                    f.setCreatedAt(Instant.now());
                    return f;
                })
                .map(fileRepository::save)
                .toList();

        // Combine new + existing
        List<File> result = new ArrayList<>(newFiles);
        result.addAll(existing.values());

        // Batch increment usageCount
        List<Long> ids = result.stream().map(File::getId).toList();
        fileRepository.incrementUsageCount(ids);

        return result;
    }

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
            File media = fromUrlToFile(mediaUrl); // New method - see below
            prepared.add(media);
            hashToUrl.put(media.getHash(), mediaUrl);

            // COVER (optional)
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

        // Persist only new files (without downloading)
        List<File> newFiles = prepared.stream()
                .filter(f -> !existing.containsKey(f.getHash()))
                .map(f -> {
                    f.setUrl(hashToUrl.get(f.getHash())); // Set URL directly
                    f.setCreatedAt(Instant.now());
                    return f;
                })
                .map(fileRepository::save)
                .toList();

        // Combine new + existing
        List<File> result = new ArrayList<>(newFiles);
        result.addAll(existing.values());

        // Batch increment usageCount
        List<Long> ids = result.stream().map(File::getId).toList();
        fileRepository.incrementUsageCount(ids);

        return result;
    }

    private File fromUrlToFile(String url) {
        String filename = url.replaceAll(".*/", "").split("[?#]")[0];
        File file = new File();
        file.setFilename(filename);
        file.setUrl(url);
        file.setHash(url);
        file.setSize(0L); // You don't know the size without downloading
        return file;
    }

    /**
     * Marks the given files for deletion by decrementing their usage count.
     * Actual removal is handled later by the cleanup cron.
     * 
     * @param files to delete
     */
    public void delete(List<File> files) {
        log.debug("Request to delete Files : {}", files);
        deleteByIds(files.stream().map(File::getId).toList());
    }

    /**
     * Marks the given files for deletion by decrementing their usage count.
     * Actual removal is handled later by the cleanup cron.
     * 
     * @param files to delete
     */
    public void deleteByUrls(List<String> urls) {
        log.debug("Request to delete Files with urls: {}", urls);
        var files = fileRepository.findByUrlIn(urls);
        delete(files);
    }

    /**
     * Decrements usageCount for all given file IDs.
     * Files reaching usageCount = 0 become eligible for cleanup.
     * 
     * [TODO] Use redis to make the deletion asynchrone.
     * 
     * @param fileIds id of Files to delete.
     */
    public void deleteByIds(List<Long> fileIds) {
        fileRepository.decrementUsageCount(fileIds);
    }

    /**
     * Periodic cleanup of files no longer referenced.
     *
     * <p>
     * Selects files with usageCount = 0.
     * Deletes the physical file from storage.
     * Removes the corresponding File rows.
     * <\p>
     */
    @Scheduled(cron = "0 0 3 * * *") // every day at 3 AM
    @Transactional
    public void cleanupUnusedFiles() {
        log.warn("Launching the cleanup schedule for unused files");

        // Get all unused files
        List<FileProjection> unusedFiles = fileRepository.findUnusedFiles();
        if (unusedFiles.isEmpty()) {
            return;
        }

        // Delete physical files
        unusedFiles.forEach(f -> fileStorageService.delete(f.getUrl()));

        // Delete database entries
        fileRepository.deleteUnusedFiles(
                unusedFiles.stream().map(FileProjection::getId).toList());
        log.debug("A total of {}x Files are deleted", unusedFiles.size());
    }

    private File fromMultipartToFile(MultipartFile media) {
        String name = media.getOriginalFilename();
        String ext = (name != null && name.contains(".")) ? name.substring(name.lastIndexOf('.') + 1).toLowerCase()
                : "";
        File file = new File();
        String hash = computeHash(media);
        // Format name
        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE); // yyyyMMdd
        String shortHash = hash.substring(0, 8);
        String filename = "-" + date + "-" + shortHash + "." + ext;

        file.setHash(hash);
        file.setFilename(filename);
        file.setType(media.getContentType());
        file.setSize(media.getSize());
        return file;
    }

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
