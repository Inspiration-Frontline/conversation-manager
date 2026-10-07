package ifl.agentbreaker.conversationmanager.domain.dtos.responses;

/**
 * Bounded edit-source provenance projected into owner and shared Round history.
 *
 * @param roundId database identity of the editing Round
 * @param fileId stable public identifier of the source image
 * @param sourceKind UPLOADED or GENERATED
 * @param sourceRoundNumber Round that produced or attached the source image, or null when unknown
 * @param resolutionKind EXPLICIT or REFERENCE_RESOLVER
 */
public record EditSourceHistory(long roundId, String fileId, String sourceKind, Long sourceRoundNumber,
                                String resolutionKind)
{
}
