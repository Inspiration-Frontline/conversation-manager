package ifl.agentbreaker.conversationmanager.domain.dtos.responses;

/** Latest visible assistant text persisted for one active Round.
 * @param roundNumber one-based Round number within the Conversation
 * @param assistantAnswer response text from the latest persisted Turn
 */
public record RoundAssistantAnswerHistory(long roundNumber, String assistantAnswer)
{
}
