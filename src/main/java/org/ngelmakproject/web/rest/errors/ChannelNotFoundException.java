package org.ngelmakproject.web.rest.errors;

public class ChannelNotFoundException extends ResourceNotFoundException {

  private static final long serialVersionUID = 1L;

  public ChannelNotFoundException() {
    super("No channel found.", "channel", "channelNotFound");
  }
}