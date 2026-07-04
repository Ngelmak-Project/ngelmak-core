package org.ngelmakproject.domain;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIncludeProperties;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * A File.
 */
@Entity
@Table(name = "file")
public class File implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "file_seq")
    @SequenceGenerator(name = "file_seq", sequenceName = "file_seq", allocationSize = 50)
    @Column(name = "id")
    private Long id;

    @JsonIgnore
    @Column(name = "hash")
    private String hash;

    @Column(name = "filename")
    private String filename;

    @Column(name = "size")
    private Long size;

    @Column(name = "duration")
    private Integer duration = 0;

    @Column(name = "url")
    private String url;

    @JsonIgnore
    @Column(name = "internal_url")
    private String internalUrl;

    @Column(name = "type")
    private String type;

    @JsonIgnore
    @Column(name = "usage_count", nullable = false)
    private Integer usageCount = 0;

    @JsonIgnore
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @JsonIncludeProperties(value = { "id" })
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "cover_id", nullable = true, foreignKey = @ForeignKey(name = "fk_file_cover", foreignKeyDefinition = "FOREIGN KEY (cover_id) REFERENCES file(id) ON DELETE SET NULL"))
    private File cover;

    public static long getSerialversionuid() {
        return serialVersionUID;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getHash() {
        return hash;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public Long getSize() {
        return size;
    }

    public void setSize(Long size) {
        this.size = size;
    }

    public Integer getDuration() {
        return duration;
    }

    public void setDuration(Integer duration) {
        this.duration = duration;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getInternalUrl() {
        return internalUrl;
    }

    public void setInternalUrl(String internalUrl) {
        this.internalUrl = internalUrl;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Integer getUsageCount() {
        return this.usageCount;
    }

    public void setUsageCount(Integer usageCount) {
        this.usageCount = usageCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public File getCover() {
        return cover;
    }

    public void setCover(File cover) {
        this.cover = cover;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof File)) {
            return false;
        }

        File file = (File) o;

        if (this.id != null && this.id.equals(file.id)) {
            return true;
        }

        if (this.url != null && this.url.equals(file.url)) {
            return true;
        }

        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "File {id=" + id + ", filename=" + filename + ", size=" + size + ", duration="
                + duration + ", type=" + type + ", usageCount="
                + usageCount + ", createdAt=" + createdAt + ", cover=" + cover + "}";
    }
}
