package ifl.agentbreaker.conversationmanager.domain.dtos.responses;

import java.util.List;

public record RoundHistoryView(String conversationId, long latestRoundNumber, List<RoundView> rounds)
{
    public record RoundView(long roundNumber, String userMessage, String assistantAnswer, String status,
                            String errorMessage, long turnCount, long startTime, long endTime,
                            List<FileView> files, List<ReferenceView> references)
    {
    }

    public record FileView(String fileId, String originalFilename, String mimeType, long fileSize,
                           String kind, String status)
    {
    }

    public record ReferenceView(String sourceConversationId, long sourceEndRoundNumber, String sourceTitle)
    {
    }
}
