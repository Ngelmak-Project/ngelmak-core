package org.ngelmakproject.domain;

import java.io.Serializable;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * The Donation entity.
 */
@Entity
@Table(name = "nk_donation")
public class Donation implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "donation_seq")
    @SequenceGenerator(name = "donation_seq", sequenceName = "donation_seq", allocationSize = 50)
    @Column(name = "id")
    private Long id;

    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private Integer amount;

    @Column(name = "content", length = 1000)
    private String message;

    @Column(name = "is_anonymous", nullable = false)
    private boolean isAnonymous = true;

    @Column(name = "name")
    private String name;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now(); // Optionnel : link to the user.

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getAmount() {
        return amount;
    }

    public void setAmount(Integer amount) {
        this.amount = amount;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public boolean isAnonymous() {
        return isAnonymous;
    }

    public void setAnonymous(boolean isAnonymous) {
        this.isAnonymous = isAnonymous;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "Donation [id=" + id + ", amount=" + amount + ", message=" + message + ", isAnonymous=" + isAnonymous
                + ", name=" + name + ", createdAt=" + createdAt + "]";
    }

}
