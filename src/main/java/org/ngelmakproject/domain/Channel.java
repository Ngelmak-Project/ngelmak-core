package org.ngelmakproject.domain;

import java.io.Serializable;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * The Channel entity.
 */
@Entity
@Table(name = "nk_channel")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class Channel implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    @Column(name = "id")
    private Long id;

    /* User (auth-service) that own the channel */
    @Column(name = "user_id", unique = true, nullable = false)
    private Long user;

    /**
     * A string or code used in URLs
     * /channel/acme-corp
     * identifier = "acme-corp"
     */
    @Column(name = "identifier", length = 30, unique = true)
    private String identifier;

    @NotNull
    @NotBlank
    @Column(name = "name", length = 100)
    private String name;

    @Column(name = "description", length = 1000)
    private String description;

    /**
     * Avatar or profile image.
     */
    @Column(name = "avatar")
    private String avatar;

    /**
     * Background image url.
     */
    @Column(name = "banner")
    private String banner;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @JsonIgnore
    @Column(name = "deleted_at", nullable = true)
    private Instant deletedAt;

    /**
     * any user can subscribe to any other user's channel which my eventually have
     * any subscriber
     */
    @OneToMany(fetch = FetchType.LAZY, mappedBy = "follower")
    @JsonIgnore
    private Set<Membership> memberships = new HashSet<>();

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "following")
    @JsonIgnore
    private Set<Membership> subscriptions = new HashSet<>();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUser() {
        return user;
    }

    public void setUser(Long user) {
        this.user = user;
    }

    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    public String getBanner() {
        return banner;
    }

    public void setBanner(String banner) {
        this.banner = banner;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }

    public Set<Membership> getMemberships() {
        return memberships;
    }

    public void setMemberships(Set<Membership> memberships) {
        this.memberships = memberships;
    }

    public Set<Membership> getSubscriptions() {
        return subscriptions;
    }

    public void setSubscriptions(Set<Membership> subscriptions) {
        this.subscriptions = subscriptions;
    }

    @Override
    public String toString() {
        return "Channel [id=" + id + ", user=" + user + ", identifier=" + identifier + ", name=" + name
                + ", description=" + description + ", avatar=" + avatar + ", banner=" + banner + ", createdAt="
                + createdAt + ", deletedAt=" + deletedAt + ", memberships=" + memberships + ", subscriptions="
                + subscriptions + "]";
    }

}
