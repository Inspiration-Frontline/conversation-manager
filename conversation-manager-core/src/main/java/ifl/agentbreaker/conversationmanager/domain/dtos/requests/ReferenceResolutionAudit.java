package ifl.agentbreaker.conversationmanager.domain.dtos.requests;

import ifl.agentbreaker.conversationmanager.domain.constants.ResolverExecutionStatus;
import lombok.Builder;

import java.time.Instant;

/**
 * Bounded audit for one image-reference-resolver Task-Agent execution submitted with the edit
 * attempt that it resolved.
 *
 * @param taskAgentId resolver Task-Agent identity
 * @param taskAgentName resolver Task-Agent name
 * @param taskAgentVersion resolver Task-Agent version
 * @param status terminal resolver status
 * @param requestId request correlation identifier
 * @param traceId distributed trace identifier
 * @param parentSpanId capability span that owns the resolver execution
 * @param taskSpanId resolver execution span
 * @param resolvedFileId selected candidate file ID, or an empty value when none was found
 * @param resolvedRoundNumber Round that produced the selected candidate, or zero when unknown
 * @param resolutionReason bounded resolver explanation retained for audit
 * @param startTime resolver execution start
 * @param endTime resolver execution end, null while undetermined
 * @param model canonical model identity used by the resolver
 */
@Builder
public record ReferenceResolutionAudit(
    long taskAgentId,
    String taskAgentName,
    int taskAgentVersion,
    ResolverExecutionStatus status,
    String requestId,
    String traceId,
    String parentSpanId,
    String taskSpanId,
    String resolvedFileId,
    long resolvedRoundNumber,
    String resolutionReason,
    Instant startTime,
    Instant endTime,
    String model)
{
}
