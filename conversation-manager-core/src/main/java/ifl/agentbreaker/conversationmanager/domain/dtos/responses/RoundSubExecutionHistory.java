package ifl.agentbreaker.conversationmanager.domain.dtos.responses;

import java.time.Instant;

/**
 * Compact persisted Task-Agent execution evidence used to show sub-agent steps under one Round.
 *
 * @param roundNumber containing Round sequence number
 * @param turnNumber model Turn the execution belongs to
 * @param capabilityKey permanent AgentBreaker capability identity the sub-agent executed
 * @param taskAgentId immutable Task-Agent identity
 * @param taskAgentName published Task-Agent name
 * @param taskAgentVersion immutable Task-Agent version
 * @param status terminal Task-Agent execution status
 * @param startTime execution start time
 * @param endTime execution end time, or {@code null} while unfinished
 * @param errorMessage bounded failure detail, or an empty value when successful
 */
public record RoundSubExecutionHistory(
    long roundNumber,
    long turnNumber,
    String capabilityKey,
    long taskAgentId,
    String taskAgentName,
    long taskAgentVersion,
    String status,
    Instant startTime,
    Instant endTime,
    String errorMessage)
{
}
