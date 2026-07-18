package org.ngelmakproject.domain;

import java.io.Serializable;
import java.time.Instant;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonIncludeProperties;

import jakarta.persistence.CascadeType;
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
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A Comment.
 */
@Entity
@Table(name = "comment")
@JsonIgnoreProperties(ignoreUnknown = true)
public class Comment implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "comment_seq")
    @SequenceGenerator(name = "comment_seq", sequenceName = "comment_seq", allocationSize = 50)
    @Column(name = "id")
    private Long id;

    @CreatedDate
    @NotNull
    @Column(name = "at", nullable = false, updatable = false)
    private Instant at;

    @LastModifiedDate
    @Column(name = "last_update")
    private Instant lastUpdate;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "content", length = 5000, nullable = false)
    @Size(max = 5000)
    private String content;

    @Column(name = "reply_count")
    private Integer replyCount = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIncludeProperties(value = { "id" })
    @JoinColumn(name = "post_id", nullable = true, foreignKey = @ForeignKey(name = "fk_comment_post", foreignKeyDefinition = "FOREIGN KEY (post_id) REFERENCES post(id) ON DELETE SET NULL"))
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY, cascade = CascadeType.REMOVE)
    @JsonIncludeProperties(value = { "id" })
    @JoinColumn(name = "reply_to_id", nullable = true, foreignKey = @ForeignKey(name = "fk_comment_reply_to", foreignKeyDefinition = "FOREIGN KEY (reply_to_id) REFERENCES reply_to(id) ON DELETE SET NULL"))
    private Comment replyTo;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false, cascade = CascadeType.REMOVE)
    @JsonIncludeProperties(value = { "id" })
    @JoinColumn(name = "channel_id", nullable = true, foreignKey = @ForeignKey(name = "fk_comment_channel", foreignKeyDefinition = "FOREIGN KEY (channel_id) REFERENCES channel(id) ON DELETE CASCADE"))
    private Channel channel;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JsonIncludeProperties(value = { "id", "url" })
    private File file;

    public Comment() {
    }

    public Long getId() {
        return this.id;
    }

    public Comment id(Long id) {
        this.setId(id);
        return this;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Instant getAt() {
        return this.at;
    }

    public Comment at(Instant at) {
        this.setAt(at);
        return this;
    }

    public void setAt(Instant at) {
        this.at = at;
    }

    public Instant getLastUpdate() {
        return this.lastUpdate;
    }

    public Comment lastUpdate(Instant lastUpdate) {
        this.setLastUpdate(lastUpdate);
        return this;
    }

    public void setLastUpdate(Instant lastUpdate) {
        this.lastUpdate = lastUpdate;
    }

    public Instant getDeleteAt() {
        return this.deletedAt;
    }

    public Comment deletedAt(Instant deletedAt) {
        this.setDeleteAt(deletedAt);
        return this;
    }

    public void setDeleteAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }

    public String getContent() {
        return this.content;
    }

    public Comment content(String content) {
        this.setContent(content);
        return this;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Post getPost() {
        return this.post;
    }

    public void setPost(Post post) {
        this.post = post;
    }

    public Comment post(Post post) {
        this.setPost(post);
        return this;
    }

    public Comment getReplyTo() {
        return this.replyTo;
    }

    public void setReplyTo(Comment comment) {
        this.replyTo = comment;
    }

    public Comment replyTo(Comment comment) {
        this.setReplyTo(comment);
        return this;
    }

    public void setReplyCount(Integer replyCount) {
        this.replyCount = replyCount;
    }

    public Integer getReplyCount() {
        return this.replyCount;
    }

    public Comment replyCount(Integer replyCount) {
        this.setReplyCount(replyCount);
        return this;
    }

    public File getFile() {
        return this.file;
    }

    public void setFile(File file) {
        this.file = file;
    }

    public Comment file(File file) {
        this.setFile(file);
        return this;
    }

    public Channel getChannel() {
        return this.channel;
    }

    public void setChannel(Channel channel) {
        this.channel = channel;
    }

    public Comment channel(Channel channel) {
        this.setChannel(channel);
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Comment)) {
            return false;
        }
        return getId() != null && getId().equals(((Comment) o).getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "Comment{" +
                "id=" + getId() +
                ", at='" + getAt() + "'" +
                ", lastUpdate='" + getLastUpdate() + "'" +
                ", content='" + getContent() + "'" +
                "}";
    }
}