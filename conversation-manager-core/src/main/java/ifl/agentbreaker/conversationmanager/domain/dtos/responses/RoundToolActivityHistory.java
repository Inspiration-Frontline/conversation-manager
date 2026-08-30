package ifl.agentbreaker.conversationmanager.domain.dtos.responses;

/**
 * Compact persisted Tool activity needed to rebuild one browser Round after refresh.
 * @param roundNumber containing Round sequence number
 * @param turnNumber containing model Turn sequence number
 * @param executionOrder zero-based call order within the Turn
 * @param toolCallId provider-generated Tool call identity
 * @param toolName provider-visible Tool name
 * @param toolKey permanent AgentBreaker Tool identity
 * @param arguments exact model-emitted JSON arguments after redaction
 * @param status terminal Tool execution status
 * @param resultContent normalized result supplied to model context
 * @param errorMessage failure detail, or an empty value when successful
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
