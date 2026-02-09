package org.ngelmakproject.domain;

import java.io.Serializable;
import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;

/**
 * A Membership.
 */
@Entity
@Table(name = "nk_membership")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class Membership implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    @Column(name = "id")
    private Long id;

    @Column(name = "at")
    private Instant at;

    @Column(name = "activate_notification")
    private Boolean activateNotification;

    @ManyToOne(optional = false)
    @NotNull
    @JsonIgnoreProperties(value = { "configuration", "user", "reports", "owners", "comments", "memberships",
            "subscriptions", "posts", "reviews" }, allowSetters = true)
    private Channel following;

    @ManyToOne(optional = false)
    @NotNull
    @JsonIgnoreProperties(value = { "configuration", "user", "reports", "owners", "comments", "memberships",
            "subscriptions", "posts", "reviews" }, allowSetters = true)
    private Channel follower; // The owner of the membership

    public Long getId() {
        return this.id;
    }

    public Membership id(Long id) {
        this.setId(id);
        return this;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Instant getAt() {
        return this.at;
    }

    public Membership at(Instant at) {
        this.setAt(at);
        return this;
    }

    public void setAt(Instant at) {
        this.at = at;
    }

    public Boolean getActivateNotification() {
        return this.activateNotification;
    }

    public Membership activateNotification(Boolean activateNotification) {
        this.setActivateNotification(activateNotification);
        return this;
    }

    public void setActivateNotification(Boolean activateNotification) {
        this.activateNotification = activateNotification;
    }

    public Channel getFollower() {
        return this.follower;
    }

    public void setFollower(Channel nkChannel) {
        this.follower = nkChannel;
    }

    public Membership follower(Channel nkChannel) {
        this.setFollower(nkChannel);
        return this;
    }

    public Channel getFollowing() {
        return this.following;
    }

    public void setFollowing(Channel nkChannel) {
        this.following = nkChannel;
    }

    public Membership following(Channel nkChannel) {
        this.setFollowing(nkChannel);
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Membership)) {
            return false;
        }
        return getId() != null && getId().equals(((Membership) o).getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "Membership{" +
                "id=" + getId() +
                ", at='" + getAt() + "'" +
                ", activateNotification='" + getActivateNotification() + "'" +
                "}";
    }
}
