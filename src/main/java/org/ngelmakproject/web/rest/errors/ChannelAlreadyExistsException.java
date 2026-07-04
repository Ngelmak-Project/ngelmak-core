package org.ngelmakproject.web.rest.errors;

public class ChannelAlreadyExistsException extends BadRequestAlertException {

    private static final long serialVersionUID = 1L;

    public ChannelAlreadyExistsException() {
        super("Channel already exists.", "channel", "channelAlreadyExists");
    }
}
