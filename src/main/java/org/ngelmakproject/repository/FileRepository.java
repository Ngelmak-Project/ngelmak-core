package org.ngelmakproject.repository;

import java.util.List;
import java.util.Optional;

import org.ngelmakproject.domain.File;
import org.ngelmakproject.repository.projection.FileProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data JPA repository for the File entity.
 */
@SuppressWarnings("unused")
public interface FileRepository extends JpaRepository<File, Long> {
    Optional<File> findByHash(String hash);

    List<File> findByHashIn(Iterable<String> hashes);

    List<File> findByUrlIn(Iterable<String> urls);

    /**
     * Increments usageCount for all files in the given list.
     *
     * <p>
     * Adds +1 to usageCount for each file ID.
     * <\p>
     *
     * @param ids list of file IDs to increment
     * @return number of rows updated
     */
    @Modifying
    @Query("""
            UPDATE File f
            SET f.usageCount = f.usageCount + 1
            WHERE f.id IN :ids
            """)
    Integer incrementUsageCount(@Param("ids") List<Long> ids);

    /**
     * Decrements usageCount for all files in the given list.
     *
     * <p>
     * Retrieves -1 to usageCount for each file ID.
     * <\p>
     *
     * @param ids list of file IDs to decrement
     * @return number of rows updated
     */
    @Modifying
    @Query("""
            UPDATE File f
            SET f.usageCount = GREATEST(0, f.usageCount - 1)
            WHERE f.id IN :ids
            """)
    Integer decrementUsageCount(@Param("ids") List<Long> ids);

    /**
     * Atomically adjusts the usage counter of a file.
     *
     * <p>
     * Increments or decrements usageCount by the given value.
     * Prevents negative values using GREATEST(0, ...).
     * <\p>
     *
     * @param fileId the ID of the file to update
     * @param count  positive to increment, negative to decrement
     * @return the number of rows updated (0 or 1).
     */
    @Modifying
    @Query("""
            UPDATE File f SET f.usageCount = GREATEST(0, f.usageCount + :count)
            WHERE f.id = :fileId
            """)
    Integer updateUsageCount(@Param("fileId") Long fileId, @Param("count") Integer count);

    /**
     * Deletes all files that are no longer referenced.
     *
     * <p>
     * Removes File rows where usageCount == 0.
     * Intended for cleanup cron jobs.
     * <\p>
     * 
     * @return the number of deleted rows.
     */
    @Modifying
    @Query("DELETE File f WHERE f.usageCount = 0")
    Integer deleteUnusedFiles();

    /**
     * Deletes all files in the given list id.
     *
     * @param ids list of files to remove.
     * @return the number of deleted rows.
     */
    @Modifying
    @Query("DELETE File f WHERE f.id IN :ids")
    Integer deleteUnusedFiles(@Param("ids") List<Long> ids);

    @Query("""
            SELECT new org.ngelmakproject.repository.projection.FileProjection(
                f.id AS id,
                f.hash AS hash,
                f.filename AS filename,
                f.size AS size,
                f.duration AS duration,
                f.url AS url,
                f.internalUrl AS internalUrl,
                f.type AS type,
                f.usageCount AS usageCount,
                f.createdAt AS createdAt,
                f.cover.id AS coverId
			)
            FROM File f
            WHERE f.usageCount = 0
            """)
    List<FileProjection> findUnusedFiles();
}
