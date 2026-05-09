package org.ngelmakproject.domain;

import java.io.Serializable;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIncludeProperties;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The Post entity.
 */
@Entity
@Table(name = "nk_post")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class Post implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    @Column(name = "id")
    private Long id;

    @Column(name = "keywords")
    private String keywords;

    @NotNull
    @Column(name = "at", nullable = false)
    private Instant at;

    @JsonIgnore
    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "last_update")
    private Instant lastUpdate;

    @Column(name = "visible")
    private Boolean visible = true;

    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    @Size(max = 10000)
    private String content; /** The content of the post. */

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIncludeProperties(value = { "id", "content" })
    private Post postReply;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @NotNull
    @JsonIncludeProperties(value = { "id", "identifier", "name", "avatar" })
    private Channel channel;

    @ManyToMany
    @JoinTable(name = "nk_post_file", joinColumns = {
            @JoinColumn(name = "post_id", referencedColumnName = "id") }, inverseJoinColumns = {
                    @JoinColumn(name = "file_id", referencedColumnName = "id") })
    private Set<File> files = new HashSet<>();

    @Column(name = "comment_count")
    private Integer commentCount = 0;

    /**
     * The Status enumeration.
     */
    public enum Status {
        PENDING,
        REJECTED,
        VALIDATED,
        SUSPENDED,
        DELETING,
        NOT_QUALIFIED,
    }

    /**
     * The Subject enumeration.
     */
    public enum Subject {
        OPEN_LETTER,
        CRITIC,
        OPINION,
        SUGGESTION,
        IDEA,
    }

    public Long getId() {
        return this.id;
    }

    public Post id(Long id) {
        this.setId(id);
        return this;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getKeywords() {
        return this.keywords;
    }

    public Post keywords(String keywords) {
        this.setKeywords(keywords);
        return this;
    }

    public void setKeywords(String keywords) {
        this.keywords = keywords;
    }

    public Instant getAt() {
        return this.at;
    }

    public Post at(Instant at) {
        this.setAt(at);
        return this;
    }

    public void setAt(Instant at) {
        this.at = at;
    }

    public Instant getDeletedAt() {
        return this.deletedAt;
    }

    public Post deletedAt(Instant deletedAt) {
        this.setDeletedAt(deletedAt);
        return this;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }

    public Instant getLastUpdate() {
        return this.lastUpdate;
    }

    public Post lastUpdate(Instant lastUpdate) {
        this.setLastUpdate(lastUpdate);
        return this;
    }

    public void setLastUpdate(Instant lastUpdate) {
        this.lastUpdate = lastUpdate;
    }

    public Boolean getVisible() {
        return this.visible;
    }

    public Post visible(Boolean visible) {
        this.setVisible(visible);
        return this;
    }

    public void setVisible(Boolean visible) {
        this.visible = visible;
    }

    public String getContent() {
        return this.content;
    }

    public Post content(String content) {
        this.setContent(content);
        return this;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Status getStatus() {
        return this.status;
    }

    public Post status(Status status) {
        this.setStatus(status);
        return this;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public Set<File> getFiles() {
        return this.files;
    }

    public void setFiles(Set<File> files) {
        this.files = files;
    }

    public Post files(Set<File> files) {
        this.setFiles(files);
        return this;
    }

    public Integer getCommentCount() {
        return this.commentCount;
    }

    public Post commentCount(Integer commentCount) {
        this.setCommentCount(commentCount);
        return this;
    }

    public void setCommentCount(Integer commentCount) {
        this.commentCount = commentCount;
    }

    public Post getPostReply() {
        return this.postReply;
    }

    public void setPostReply(Post postReply) {
        this.postReply = postReply;
    }

    public Post postReply(Post postReply) {
        this.setPostReply(postReply);
        return this;
    }

    public Channel getChannel() {
        return this.channel;
    }

    public void setChannel(Channel nkChannel) {
        this.channel = nkChannel;
    }

    public Post channel(Channel nkChannel) {
        this.setChannel(nkChannel);
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Post)) {
            return false;
        }
        return getId() != null && getId().equals(((Post) o).getId());
    }

    @Override
    public int hashCode() {
        // see
        // https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "Post{" +
                "id=" + getId() +
                ", keywords='" + getKeywords() + "'" +
                ", at='" + getAt() + "'" +
                ", lastUpdate='" + getLastUpdate() + "'" +
                ", visible='" + getVisible() + "'" +
                ", content='" + getContent() + "'" +
                ", status='" + getStatus() + "'" +
                ", reply='" + getPostReply() + "'" +
                "}";
    }
}
