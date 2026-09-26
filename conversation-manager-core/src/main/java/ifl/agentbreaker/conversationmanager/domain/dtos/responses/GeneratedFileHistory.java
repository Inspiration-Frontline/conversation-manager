package ifl.agentbreaker.conversationmanager.domain.dtos.responses;

/** Historical projection of one generated artifact relation and its durable file metadata. */
public record GeneratedFileHistory(
    long roundNumber,
    long fileResourceId,
    String fileId,
    String originalFilename,
    String mimeType,
    long fileSize,
    Integer width,
    Integer height,
    String kind,
    String status,
    String outputKind,
    String outputStatus,
    long sourceTurnNumber,
    int outputOrder)
{
}
