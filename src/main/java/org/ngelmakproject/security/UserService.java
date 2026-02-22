package org.ngelmakproject.security;

import java.util.Optional;
import java.util.Set;

import org.ngelmakproject.service.TicketService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Service Implementation for managing
 * {@link org.ngelmakproject.domain.Channel}.
 */
public abstract class UserService {
    private static final Logger log = LoggerFactory.getLogger(TicketService.class);

    /**
     * Internal representation of the authenticated user.
     */
    public record UserPrincipal(
            Long id,
            String username,
            Set<String> authorities) {
    }

    /**
     * Retrieves the currently authenticated user.
     *
     * <p>
     * This method is designed to be safe even when invoked in contexts where
     * authentication is not guaranteed (e.g., unsecured endpoints). It performs
     * several defensive checks to avoid runtime exceptions such as
     * {@link ClassCastException} or {@link NullPointerException}.
     * </p>
     *
     * @return an {@code Optional<UserPrincipal>} for the authenticated user, or
     *         empty
     *         if
     *         no valid authenticated user is present.
     */
    public static Optional<UserPrincipal> getAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        // No authentication available
        if (authentication == null) {
            return Optional.empty();
        }
        // Anonymous or not authenticated
        if (!authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }
        Object principal = authentication.getPrincipal();
        // Principal is not your expected custom user type
        if (!(principal instanceof UserPrincipal userPrincipal)) {
            return Optional.empty();
        }
        log.debug("​🦋 User details {}", principal);
        return Optional.of(userPrincipal);
    }
}
