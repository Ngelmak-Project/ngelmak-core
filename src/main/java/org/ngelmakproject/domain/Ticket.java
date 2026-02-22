package org.ngelmakproject.domain;

import java.io.Serializable;
import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonIncludeProperties;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;

/**
 * A Ticket.
 */
@Entity
@Table(name = "nk_ticket")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class Ticket implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    @Column(name = "id")
    private Long id;

    @NotNull
    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt = Instant.now();

    @Column(name = "resolved")
    private Boolean resolved;

    @NotNull
    @Column(name = "description", length = 1000, nullable = false)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    private File evidence;

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = { "reports", "comments", "channel" }, allowSetters = true)
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = { "reports", "comments", "post", "replayto", "channel" }, allowSetters = true)
    private Comment comment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = { "configuration", "user", "reports", "owners", "comments", "memberships",
            "subscriptions", "posts", "reviews" }, allowSetters = true)
    private Channel channel;

    /* User (auth-service) that issued the ticket */
    @NotNull
    @Column(name = "issued_by_id", nullable = false)
    private Long issuedBy;

    /* User (auth-service) that handled the ticket */
    @Column(name = "handled_by_id")
    private Long handledBy;

    /* User (auth-service) responsible for handling the ticket */
    @Column(name = "assigned_to_id")
    private Long assignedTo;

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

    public Boolean getResolved() {
        return resolved;
    }

    public void setResolved(Boolean resolved) {
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



// ngelmak-core  | 2026-02-22T11:45:12.210Z DEBUG 1 --- [nio-5742-exec-1] org.hibernate.SQL                        : select t1_0.id,t1_0.assigned_to_id,c1_0.id,c1_0.avatar,c1_0.banner,c1_0.created_at,c1_0.deleted_at,c1_0.description,c1_0.identifier,c1_0.name,c1_0.user_id,c2_0.id,c2_0.at,c2_0.channel_id,c2_0.content,c2_0.deleted_at,c2_0.file_id,c2_0.last_update,c2_0.post_id,c2_0.reply_count,c2_0.reply_to_id,t1_0.description,e1_0.id,e1_0.cover_id,e1_0.created_at,e1_0.duration,e1_0.filename,e1_0.hash,e1_0.size,e1_0.type,e1_0.url,e1_0.usage_count,t1_0.handled_by_id,t1_0.issued_at,t1_0.issued_by_id,p2_0.id,p2_0.at,p2_0.channel_id,p2_0.comment_count,p2_0.content,p2_0.deleted_at,p2_0.keywords,p2_0.last_update,p2_0.post_reply_id,p2_0.status,p2_0.visibility,t1_0.resolved from nk_ticket t1_0 left join nk_channel c1_0 on c1_0.id=t1_0.channel_id left join nk_comment c2_0 on c2_0.id=t1_0.comment_id left join nk_file e1_0 on e1_0.id=t1_0.evidence_id left join nk_post p2_0 on p2_0.id=t1_0.post_id where t1_0.id=?
// ngelmak-core  | 2026-02-22T11:45:12.259Z DEBUG 1 --- [nio-5742-exec-1] o.s.w.s.m.m.a.HttpEntityMethodProcessor  : Using 'application/json', given [*/*] and supported [application/json, application/*+json]
// ngelmak-core  | 2026-02-22T11:45:12.263Z DEBUG 1 --- [nio-5742-exec-1] o.s.w.s.m.m.a.HttpEntityMethodProcessor  : Writing [Ticket [id=2452, issuedAt=2026-02-17T12:06:02.595580Z, resolved=null, description=Contenu mensonger! (truncated)...]
// ngelmak-core  | 2026-02-22T11:45:12.283Z DEBUG 1 --- [nio-5742-exec-1] org.hibernate.SQL                        : select f1_0.post_id,f1_1.id,c1_0.id,c1_0.cover_id,c1_0.created_at,c1_0.duration,c1_0.filename,c1_0.hash,c1_0.size,c1_0.type,c1_0.url,c1_0.usage_count,f1_1.created_at,f1_1.duration,f1_1.filename,f1_1.hash,f1_1.size,f1_1.type,f1_1.url,f1_1.usage_count from nk_post_file f1_0 join nk_file f1_1 on f1_1.id=f1_0.file_id left join nk_file c1_0 on c1_0.id=f1_1.cover_id where f1_0.post_id=?
// ngelmak-core  | 2026-02-22T11:45:12.287Z DEBUG 1 --- [nio-5742-exec-1] o.s.web.servlet.DispatcherServlet        : Completed 200 OK
