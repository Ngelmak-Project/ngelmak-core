package org.ngelmakproject.web.rest.dto;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.ngelmakproject.domain.Reaction;

public record ReactionSummaryDTO(
        Map<String, Integer> counts,
        String reactedByCurrentUser,
        Long reactionId) {

    /**
     * Builds a ReactionSummaryDTO from a list of reactions for a single post.
     *
     * @param reactions     reactions belonging to the same post
     * @param currentUserId the ID of the user viewing the post (nullable)
     */
    public static ReactionSummaryDTO from(List<Reaction> reactions, Long currentUserId) {

        Map<String, Integer> counts = new HashMap<>();
        String userEmoji = null;
        Long userReactionId = null;

        for (Reaction reaction : reactions) {
            // Count emoji occurrences
            counts.merge(reaction.getEmoji(), 1, Integer::sum);

            // Detect current user's reaction
            if (currentUserId != null && currentUserId.equals(reaction.getChannel().getId())) {
                userEmoji = reaction.getEmoji();
                userReactionId = reaction.getId();
            }
        }

        return new ReactionSummaryDTO(counts, userEmoji, userReactionId);
    }
}
