package com.example.demo.event;

/**
 * Event published by U6 when a student confirms a match.
 *
 * Spring publishes this event synchronously in the same transaction.
 */
public record MatchConfirmedEvent(
        Long matchId,
        Long applicationId,
        Long positionId,
        Long roundId
) {
}
