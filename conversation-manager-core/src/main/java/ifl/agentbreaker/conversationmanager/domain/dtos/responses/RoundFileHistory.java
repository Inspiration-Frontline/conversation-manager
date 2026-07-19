package ifl.agentbreaker.conversationmanager.domain.dtos.responses;

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
