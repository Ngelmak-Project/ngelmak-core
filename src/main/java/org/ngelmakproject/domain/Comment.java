package org.ngelmakproject.domain;

import java.io.Serializable;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonIncludeProperties;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;

/**
 * A Comment.
 */
@Entity
@Table(name = "nk_comment")
@JsonIgnoreProperties(ignoreUnknown = true)
public class Comment implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    @Column(name = "id")
    private Long id;

    @NotNull
    @Column(name = "at", nullable = false)
    private Instant at;

    @Column(name = "last_update")
    private Instant lastUpdate;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "content", length = 1000, nullable = false)
    private String content;

    @ManyToOne(fetch = FetchType.LAZY, cascade = CascadeType.REMOVE)
    @JsonIncludeProperties(value = { "id" })
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY, cascade = CascadeType.REMOVE)
    @JsonIncludeProperties(value = { "id" })
    private Comment replyTo;

    @Column(name = "reply_count")
    private Integer replyCount = 0;

    @ManyToOne(fetch = FetchType.LAZY, optional = false, cascade = CascadeType.REMOVE)
    @NotNull
    @JsonIncludeProperties(value = { "id" })
    private Channel channel;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JsonIncludeProperties(value = { "id", "url" })
    private File file;

    /**
     * a ticket can be related to a abusive comment.
     */
    @OneToMany(fetch = FetchType.LAZY, mappedBy = "commentRelated")
    @JsonIgnore
    private Set<Ticket> reports = new HashSet<>();

    /**
     * a comment can have multiple subcomments (reply), each issued by one user.
     */
    @OneToMany(fetch = FetchType.LAZY, mappedBy = "replyTo")
    @JsonIgnore
    private Set<Comment> comments = new HashSet<>();

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

    public Set<Ticket> getReports() {
        return this.reports;
    }

    public void setReports(Set<Ticket> tickets) {
        if (this.reports != null) {
            this.reports.forEach(i -> i.setCommentRelated(null));
        }
        if (tickets != null) {
            tickets.forEach(i -> i.setCommentRelated(this));
        }
        this.reports = tickets;
    }

    public Comment reports(Set<Ticket> tickets) {
        this.setReports(tickets);
        return this;
    }

    public Comment addReports(Ticket ticket) {
        this.reports.add(ticket);
        ticket.setCommentRelated(this);
        return this;
    }

    public Comment removeReports(Ticket ticket) {
        this.reports.remove(ticket);
        ticket.setCommentRelated(null);
        return this;
    }

    public Set<Comment> getComments() {
        return this.comments;
    }

    public void setComments(Set<Comment> comments) {
        if (this.comments != null) {
            this.comments.forEach(i -> i.setReplyTo(null));
        }
        if (comments != null) {
            comments.forEach(i -> i.setReplyTo(this));
        }
        this.comments = comments;
    }

    public Comment comments(Set<Comment> comments) {
        this.setComments(comments);
        return this;
    }

    public Comment addComment(Comment comment) {
        this.comments.add(comment);
        comment.setReplyTo(this);
        return this;
    }

    public Comment removeComment(Comment comment) {
        this.comments.remove(comment);
        comment.setReplyTo(null);
        return this;
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
