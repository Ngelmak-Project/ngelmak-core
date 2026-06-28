package org.ngelmakproject.domain;

import java.io.Serializable;
import java.time.LocalDate;

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

/**
 * A Subscription.
 */
@Entity
@Table(name = "subscription")
public class Subscription implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "subscription_seq")
    @SequenceGenerator(name = "subscription_seq", sequenceName = "subscription_seq", allocationSize = 50)
    @Column(name = "id")
    private Long id;

    @Column(name = "subscribed_at", nullable = false, updatable = false)
    private LocalDate subscribedAt = LocalDate.now();

    @Column(name = "notifications_enabled", nullable = false)
    private boolean notificationsEnabled = true;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subscriber_id", nullable = false, foreignKey = @ForeignKey(name = "fk_subscription_subscriber", foreignKeyDefinition = "FOREIGN KEY (subscriber_id) REFERENCES channel(id) ON DELETE CASCADE"))
    private Channel subscriber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subscribed_to_id", nullable = false, foreignKey = @ForeignKey(name = "fk_subscription_subscribed_to", foreignKeyDefinition = "FOREIGN KEY (subscribed_to_id) REFERENCES channel(id) ON DELETE CASCADE"))
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
