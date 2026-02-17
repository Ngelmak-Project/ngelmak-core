package org.ngelmakproject.web.rest.errors;

public class UnauthorizedResourceAccessException extends BadRequestAlertException {

    private static final long serialVersionUID = 1L;

    public UnauthorizedResourceAccessException(String entityName) {
        super("An authenticated uer is not allowed to access resource ", entityName,
                "unauthorizedResourceAccess");
    }

    public UnauthorizedResourceAccessException(Long resourceId, String entityName) {
        super("An authenticated uer is not allowed to access resource " + resourceId, entityName,
                "unauthorizedResourceAccess");
    }

    public UnauthorizedResourceAccessException(Long userId, Long resourceId, String entityName) {
        super("User " + userId + " is not allowed to access resource " + resourceId, entityName,
                "unauthorizedResourceAccess");
    }
}
