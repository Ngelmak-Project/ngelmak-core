package org.ngelmakproject.domain;

import java.io.Serializable;
import java.time.Instant;

import org.springframework.data.annotation.CreatedDate;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;

/**
 * A Ticket.
 */
@Entity
@Table(name = "ticket")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class Ticket implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ticket_seq")
    @SequenceGenerator(name = "ticket_seq", sequenceName = "ticket_seq", allocationSize = 50)
    @Column(name = "id")
    private Long id;

    @CreatedDate
    @NotNull
    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt = Instant.now();

    @Column(name = "resolved")
    private boolean resolved = false;

    @NotNull
    @Column(name = "description", length = 1000, nullable = false)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false, length = 20)
    private Visibility visibility = Visibility.PUBLIC;

    // The user whose action was reported
    @Column(name = "target_user")
    private Long targetUser;

    /* User (auth-service) that issued the ticket */
    @Column(name = "issued_by_id")
    private Long issuedBy;

    /* User (auth-service) that handled the ticket */
    @Column(name = "handled_by_id")
    private Long handledBy;

    /* User (auth-service) responsible for handling the ticket */
    @Column(name = "assigned_to_id")
    private Long assignedTo;

    @ManyToOne(fetch = FetchType.LAZY)
    private File evidence;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false, foreignKey = @ForeignKey(name = "fk_ticket_post", foreignKeyDefinition = "FOREIGN KEY (post_id) REFERENCES post(id) ON DELETE CASCADE"))
    @JsonIgnoreProperties(value = { "reports", "comments", "channel" }, allowSetters = true)
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "comment_id", nullable = false, foreignKey = @ForeignKey(name = "fk_ticket_comment", foreignKeyDefinition = "FOREIGN KEY (comment_id) REFERENCES comment(id) ON DELETE CASCADE"))
    @JsonIgnoreProperties(value = { "reports", "comments", "post", "replayto", "channel" }, allowSetters = true)
    private Comment comment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "channel_id", nullable = false, foreignKey = @ForeignKey(name = "fk_ticket_channel", foreignKeyDefinition = "FOREIGN KEY (channel_id) REFERENCES channel(id) ON DELETE CASCADE"))
    @JsonIgnoreProperties(value = { "configuration", "user", "reports", "owners", "comments", "memberships",
            "subscriptions", "posts", "reviews" }, allowSetters = true)
    private Channel channel;

    public enum Visibility {
        PUBLIC, // visible to target user
        MODERATOR, // visible only to moderators/admins
        ADMIN // internal system messages
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public void setIssuedAt(Instant issuedAt) {
        this.issuedAt = issuedAt;
    }

    public boolean isResolved() {
        return resolved;
    }

    public void setResolved(boolean resolved) {
        this.resolved = resolved;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public File getEvidence() {
        return evidence;
    }

    public void setEvidence(File evidence) {
        this.evidence = evidence;
    }

    public Post getPost() {
        return post;
    }

    public void setPost(Post post) {
        this.post = post;
    }

    public Comment getComment() {
        return comment;
    }

    public void setComment(Comment comment) {
        this.comment = comment;
    }

    public Channel getChannel() {
        return channel;
    }

    public void setChannel(Channel channel) {
        this.channel = channel;
    }

    public Long getTargetUser() {
        return targetUser;
    }

    public void setTargetUser(Long targetUser) {
        this.targetUser = targetUser;
    }

    public Long getIssuedBy() {
        return issuedBy;
    }

    public void setIssuedBy(Long issuedBy) {
        this.issuedBy = issuedBy;
    }

    public Long getHandledBy() {
        return handledBy;
    }

    public void setHandledBy(Long handledBy) {
        this.handledBy = handledBy;
    }

    public Long getAssignedTo() {
        return assignedTo;
    }

    public void setAssignedTo(Long assignedTo) {
        this.assignedTo = assignedTo;
    }

    @Override
    public String toString() {
        return "Ticket [id=" + id + ", issuedAt=" + issuedAt + ", resolved=" + resolved + ", description=" + description
                + ", evidence=" + evidence + ", post=" + post + ", comment=" + comment + ", channel=" + channel
                + ", issuedBy=" + issuedBy + ", handledBy=" + handledBy + ", assignedTo=" + assignedTo + "]";
    }
}