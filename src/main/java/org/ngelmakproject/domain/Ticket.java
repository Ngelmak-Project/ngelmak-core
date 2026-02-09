package org.ngelmakproject.domain;

import java.io.Serializable;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import org.ngelmakproject.domain.enumeration.TicketType;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

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

    /**
     * main title of the ticket
     */
    @NotNull
    @Size(min = 50, max = 200)
    @Column(name = "object", length = 200, nullable = false)
    private String object;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private TicketType type;

    @NotNull
    @Column(name = "at", nullable = false)
    private Instant at;

    @Column(name = "closed")
    private Boolean closed;

    @Column(name = "content")
    private String content;

    /**
     * a review is either related to a ticket or is a reply to another review.
     */
    @OneToMany(fetch = FetchType.LAZY, mappedBy = "ticket")
    @JsonIgnoreProperties(value = { "reviews", "channel", "ticket", "replyto" }, allowSetters = true)
    private Set<Review> reviews = new HashSet<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = { "attachments", "reports", "comments", "channel" }, allowSetters = true)
    private Post postRelated;

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = { "reports", "comments", "post", "replayto", "channel" }, allowSetters = true)
    private Comment commentRelated;

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = { "configuration", "user", "reports", "owners", "comments", "memberships",
            "subscriptions", "posts", "reviews" }, allowSetters = true)
    private Channel channelRelated;

    @ManyToOne(optional = false)
    @NotNull
    @JsonIgnoreProperties(value = { "configuration", "user", "reports", "owners", "comments", "memberships",
            "subscriptions", "posts", "reviews" }, allowSetters = true)
    private Channel issuedby;

    public Long getId() {
        return this.id;
    }

    public Ticket id(Long id) {
        this.setId(id);
        return this;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getObject() {
        return this.object;
    }

    public Ticket object(String object) {
        this.setObject(object);
        return this;
    }

    public void setObject(String object) {
        this.object = object;
    }

    public TicketType getType() {
        return this.type;
    }

    public Ticket type(TicketType type) {
        this.setType(type);
        return this;
    }

    public void setType(TicketType type) {
        this.type = type;
    }

    public Instant getAt() {
        return this.at;
    }

    public Ticket at(Instant at) {
        this.setAt(at);
        return this;
    }

    public void setAt(Instant at) {
        this.at = at;
    }

    public Boolean getClosed() {
        return this.closed;
    }

    public Ticket closed(Boolean closed) {
        this.setClosed(closed);
        return this;
    }

    public void setClosed(Boolean closed) {
        this.closed = closed;
    }

    public String getContent() {
        return this.content;
    }

    public Ticket content(String content) {
        this.setContent(content);
        return this;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Set<Review> getReviews() {
        return this.reviews;
    }

    public void setReviews(Set<Review> reviews) {
        if (this.reviews != null) {
            this.reviews.forEach(i -> i.setTicket(null));
        }
        if (reviews != null) {
            reviews.forEach(i -> i.setTicket(this));
        }
        this.reviews = reviews;
    }

    public Ticket reviews(Set<Review> reviews) {
        this.setReviews(reviews);
        return this;
    }

    public Ticket addReview(Review review) {
        this.reviews.add(review);
        review.setTicket(this);
        return this;
    }

    public Ticket removeReview(Review review) {
        this.reviews.remove(review);
        review.setTicket(null);
        return this;
    }

    public Post getPostRelated() {
        return this.postRelated;
    }

    public void setPostRelated(Post post) {
        this.postRelated = post;
    }

    public Ticket postRelated(Post post) {
        this.setPostRelated(post);
        return this;
    }

    public Comment getCommentRelated() {
        return this.commentRelated;
    }

    public void setCommentRelated(Comment comment) {
        this.commentRelated = comment;
    }

    public Ticket commentRelated(Comment comment) {
        this.setCommentRelated(comment);
        return this;
    }

    public Channel getChannelRelated() {
        return this.channelRelated;
    }

    public void setChannelRelated(Channel nkChannel) {
        this.channelRelated = nkChannel;
    }

    public Ticket channelRelated(Channel nkChannel) {
        this.setChannelRelated(nkChannel);
        return this;
    }

    public Channel getIssuedby() {
        return this.issuedby;
    }

    public void setIssuedby(Channel nkChannel) {
        this.issuedby = nkChannel;
    }

    public Ticket issuedby(Channel nkChannel) {
        this.setIssuedby(nkChannel);
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Ticket)) {
            return false;
        }
        return getId() != null && getId().equals(((Ticket) o).getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "Ticket{" +
                "id=" + getId() +
                ", object='" + getObject() + "'" +
                ", type='" + getType() + "'" +
                ", at='" + getAt() + "'" +
                ", closed='" + getClosed() + "'" +
                ", content='" + getContent() + "'" +
                "}";
    }
}
