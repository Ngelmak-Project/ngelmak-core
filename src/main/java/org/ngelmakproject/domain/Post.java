package org.ngelmakproject.domain;

import java.io.Serializable;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

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
@Table(name = "post")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class Post implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "post_seq")
    @SequenceGenerator(name = "post_seq", sequenceName = "post_seq", allocationSize = 50)
    @Column(name = "id")
    private Long id;

    @Column(name = "keywords")
    private String keywords;

    @CreatedDate
    @NotNull
    @Column(name = "at", nullable = false, updatable = false)
    private Instant at;

    @JsonIgnore
    @Column(name = "deleted_at")
    private Instant deletedAt;

    @LastModifiedDate
    @Column(name = "last_update")
    private Instant lastUpdate;

    @Column(name = "visible")
    private Boolean visible = true;

    /** The content of the post. */
    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    @Size(max = 10_000)
    private String content;

    @Column(name = "comment_count")
    private Integer commentCount = 0;

    @JsonIncludeProperties(value = { "id", "content", "at", "channel" })
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "reply_to_id", nullable = true, foreignKey = @ForeignKey(name = "fk_post_replyTo", foreignKeyDefinition = "FOREIGN KEY (reply_to_id) REFERENCES post(id) ON DELETE SET NULL"))
    private Post replyTo;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @NotNull
    @JsonIncludeProperties(value = { "id", "identifier", "name", "avatar" })
    @JoinColumn(name = "channel_id", nullable = true, foreignKey = @ForeignKey(name = "fk_post_postchannel", foreignKeyDefinition = "FOREIGN KEY (channel_id) REFERENCES post(id) ON DELETE CASCADE"))
    private Channel channel;

    @ManyToMany
    @JoinTable(name = "post_file", joinColumns = {
            @JoinColumn(name = "post_id", referencedColumnName = "id") }, inverseJoinColumns = {
                    @JoinColumn(name = "file_id", referencedColumnName = "id") })
    private Set<File> files = new HashSet<>();


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

    public Post getreplyTo() {
        return this.replyTo;
    }

    public void setreplyTo(Post replyTo) {
        this.replyTo = replyTo;
    }

    public Post replyTo(Post replyTo) {
        this.setreplyTo(replyTo);
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
                ", reply='" + getreplyTo() + "'" +
                ", files='" + getFiles() + "'" +
                "}";
    }
}
