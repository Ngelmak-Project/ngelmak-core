package org.ngelmakproject.config;

/**
 * Application constants.
 */
public final class Constants {

    // Regex for acceptable logins
    public static final String LOGIN_REGEX = "^(?>[a-zA-Z0-9!$&*+=?^_`{|}~.-]+@[a-zA-Z0-9-]+(?:\\.[a-zA-Z0-9-]+)*)|(?>[_.@A-Za-z0-9-]+)$";

    public static final String SYSTEM = "system";
    public static final String DEFAULT_LANGUAGE = "fr";
    public static final String DEFAULT_ATTACHMENT_LOCAL_DIRECTORY = "attachment-repos";

    // 10000 chars max for post content.
    public static final int MAX_POST_LENGTH = 10000;
    // 2000 chars max for comment content.
    public static final int MAX_COMMENT_LENGTH = 2000;
}