package org.ngelmakproject.domain;

import java.io.Serializable;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * A Subscription.
 */
@Entity
@Table(name = "nk_subscription")
public class Subscription implements Serializable {
    private static final long serialVersionUID = 1L;
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    private Long id;

    @Column(name = "subscribed_at", nullable = false, updatable = false)
    private LocalDate subscribedAt = LocalDate.now();

    @Column(name = "notifications_enabled", nullable = false)
    private boolean notificationsEnabled = true;

    @ManyToOne(optional = false)
    @JoinColumn(nullable = false)
    private Channel subscriber;

    @ManyToOne(optional = false)
    @JoinColumn(nullable = false)
    private Channel subscribedTo;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocalDate getSubscribedAt() {
        return subscribedAt;
    }

    public void setSubscribedAt(LocalDate subscribedAt) {
        this.subscribedAt = subscribedAt;
    }

    public boolean isNotificationsEnabled() {
        return notificationsEnabled;
    }

    public void setNotificationsEnabled(boolean notificationsEnabled) {
        this.notificationsEnabled = notificationsEnabled;
    }

    public Channel getSubscriber() {
        return subscriber;
    }

    public void setSubscriber(Channel subscriber) {
        this.subscriber = subscriber;
    }

    public Channel getSubscribedTo() {
        return subscribedTo;
    }

    public void setSubscribedTo(Channel subscribedTo) {
        this.subscribedTo = subscribedTo;
    }

    @Override
    public String toString() {
        return "Subscription [id=" + id + ", subscribedAt=" + subscribedAt + ", notificationsEnabled="
                + notificationsEnabled
                + ", subscriber=" + subscriber + ", subscribedTo=" + subscribedTo + "]";
    }
}
