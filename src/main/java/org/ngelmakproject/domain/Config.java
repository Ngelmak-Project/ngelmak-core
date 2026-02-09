package org.ngelmakproject.domain;

import java.io.Serializable;
import java.time.Instant;

import org.ngelmakproject.domain.enumeration.Accessibility;
import org.ngelmakproject.domain.enumeration.Visibility;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * A Config.
 */
@Entity
@Table(name = "nk_config")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class Config implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    @Column(name = "id")
    private Long id;

    @Column(name = "last_update")
    private Instant lastUpdate;

    @Enumerated(EnumType.STRING)
    @Column(name = "default_accessibility")
    private Accessibility defaultAccessibility;

    @Enumerated(EnumType.STRING)
    @Column(name = "default_visibility")
    private Visibility defaultVisibility;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Instant getLastUpdate() {
        return lastUpdate;
    }

    public void setLastUpdate(Instant lastUpdate) {
        this.lastUpdate = lastUpdate;
    }

    public Accessibility getDefaultAccessibility() {
        return defaultAccessibility;
    }

    public void setDefaultAccessibility(Accessibility defaultAccessibility) {
        this.defaultAccessibility = defaultAccessibility;
    }

    public Visibility getDefaultVisibility() {
        return defaultVisibility;
    }

    public void setDefaultVisibility(Visibility defaultVisibility) {
        this.defaultVisibility = defaultVisibility;
    }

    @Override
    public String toString() {
        return "Config [id=" + id + ", lastUpdate=" + lastUpdate + ", defaultAccessibility=" + defaultAccessibility
                + ", defaultVisibility=" + defaultVisibility + "]";
    }

}
