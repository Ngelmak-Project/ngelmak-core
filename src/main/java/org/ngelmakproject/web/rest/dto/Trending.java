package org.ngelmakproject.web.rest.dto;

import java.util.List;

public record Trending(List<ActiveChannel> topActiveChannels, List<PostDTO> trendingPosts,
        List<PostDTO> mostCommentedPosts) {
}
