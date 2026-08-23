package ifl.agentbreaker.conversationmanager.domain.dtos.responses;

/**
 * Compact persisted Tool activity needed to rebuild one browser Round after refresh.
 */
public record RoundToolActivityHistory(
    long roundNumber,
    long turnNumber,
    int executionOrder,
    String toolCallId,
    String toolName,
    String toolKey,
    String arguments,
    String status,
    String resultContent,
    String errorMessage)
{
}
