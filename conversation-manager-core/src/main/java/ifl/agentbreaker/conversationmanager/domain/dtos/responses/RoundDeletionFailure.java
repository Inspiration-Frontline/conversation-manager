package ifl.agentbreaker.conversationmanager.domain.dtos.responses;

/** One requested Round that was not logically deleted.
 * @param roundNumber Round number that failed or was not attempted
 * @param code stable Conversation error code
 * @param message client-safe failure explanation
 */
public record RoundDeletionFailure(long roundNumber, int code, String message)
{
}
