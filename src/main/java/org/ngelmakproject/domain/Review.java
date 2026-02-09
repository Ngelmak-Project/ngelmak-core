package org.ngelmakproject.domain;

import java.io.Serializable;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import org.ngelmakproject.domain.enumeration.Status;

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

/**
 * A Review.
 */
@Entity
@Table(name = "nk_review")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class Review implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    @Column(name = "id")
    private Long id;

    @NotNull
    @Column(name = "at", nullable = false)
    private Instant at;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status;

    /**
     * number of minutes to wait before timeout.
     */
    @NotNull
    @Column(name = "timeout", nullable = false)
    private Integer timeout;

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "replyto")
    @JsonIgnoreProperties(value = { "reviews", "channel", "ticket", "replyto" }, allowSetters = true)
    private Set<Review> reviews = new HashSet<>();

    @ManyToOne(optional = false)
    @NotNull
    @JsonIgnoreProperties(value = { "configuration", "user", "reports", "owners", "comments", "memberships",
            "subscriptions", "posts", "reviews" }, allowSetters = true)
    private Channel channel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = { "reviews", "postRelated", "commentRelated", "channelRelated",
            "issuedby" }, allowSetters = true)
    private Ticket ticket;

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = { "reviews", "channel", "ticket", "replyto" }, allowSetters = true)
    private Review replyto;

    public Long getId() {
        return this.id;
    }

    public Review id(Long id) {
        this.setId(id);
        return this;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Instant getAt() {
        return this.at;
    }

    public Review at(Instant at) {
        this.setAt(at);
        return this;
    }

    public void setAt(Instant at) {
        this.at = at;
    }

    public Status getStatus() {
        return this.status;
    }

    public Review status(Status status) {
        this.setStatus(status);
        return this;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public Integer getTimeout() {
        return this.timeout;
    }

    public Review timeout(Integer timeout) {
        this.setTimeout(timeout);
        return this;
    }

    public void setTimeout(Integer timeout) {
        this.timeout = timeout;
    }

    public Set<Review> getReviews() {
        return this.reviews;
    }

    public void setReviews(Set<Review> reviews) {
        if (this.reviews != null) {
            this.reviews.forEach(i -> i.setReplyTo(null));
        }
        if (reviews != null) {
            reviews.forEach(i -> i.setReplyTo(this));
        }
        this.reviews = reviews;
    }

    public Review reviews(Set<Review> reviews) {
        this.setReviews(reviews);
        return this;
    }

    public Review addReview(Review review) {
        this.reviews.add(review);
        review.setReplyTo(this);
        return this;
    }

    public Review removeReview(Review review) {
        this.reviews.remove(review);
        review.setReplyTo(null);
        return this;
    }

    public Channel getChannel() {
        return this.channel;
    }

    public void setChannel(Channel nkChannel) {
        this.channel = nkChannel;
    }

    public Review channel(Channel nkChannel) {
        this.setChannel(nkChannel);
        return this;
    }

    public Ticket getTicket() {
        return this.ticket;
    }

    public void setTicket(Ticket ticket) {
        this.ticket = ticket;
    }

    public Review ticket(Ticket ticket) {
        this.setTicket(ticket);
        return this;
    }

    public Review getReplyTo() {
        return this.replyto;
    }

    public void setReplyTo(Review review) {
        this.replyto = review;
    }

    public Review replyto(Review review) {
        this.setReplyTo(review);
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Review)) {
            return false;
        }
        return getId() != null && getId().equals(((Review) o).getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "Review{" +
                "id=" + getId() +
                ", at='" + getAt() + "'" +
                ", status='" + getStatus() + "'" +
                ", timeout=" + getTimeout() +
                "}";
    }
}
