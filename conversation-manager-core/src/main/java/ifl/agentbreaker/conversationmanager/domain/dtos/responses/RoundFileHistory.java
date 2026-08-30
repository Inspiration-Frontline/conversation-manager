package ifl.agentbreaker.conversationmanager.domain.dtos.responses;

/**
 * Stable file summary attached to one historical Round.
 *
 * @param roundNumber Containing Round number
 * @param fileResourceId Internal file-resource identity
 * @param fileId Stable public file identity
 * @param originalFilename User-provided display filename
 * @param mimeType Validated media type
 * @param fileSize Persisted byte size
 * @param kind Resolved Conversation file kind
 * @param status Terminal or processing file status
 */
public record RoundFileHistory(
    long roundNumber,
    long fileResourceId,
    String fileId,
    String originalFilename,
    String mimeType,
    long fileSize,
    String kind,
    String status)
{
}
