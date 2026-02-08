package org.ngelmakproject.domain.enumeration;

/**
 * The NotificationType enumeration.
 */
public enum NotificationType {
    PROJECT_NEWS, // For announcements about the evolution of Ngelmak platform.
    FEATURE_UPDATE, // For new releases, improvements, or beta features.
    MAINTENANCE, // Useful for scheduled downtime or technical operations.
    COMMUNITY_ALERT, // Covers anything urgent or important that affects all users. (rules, safety, etc.)
    GEOPOLITICAL_INFO // Matches Ngelmak requirement for global context relevant to Ngelmak audience.
}
