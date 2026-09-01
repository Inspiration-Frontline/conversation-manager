package ifl.agentbreaker.conversationmanager.domain.dtos.responses;

import java.util.List;

/** Result of deleting one validated active Round suffix.
 * @param deletedRoundNumbers successfully deleted values in descending order
 * @param failures failed and unattempted values in descending order
 */
public record RoundDeletionResult(
    List<Long> deletedRoundNumbers,
    List<RoundDeletionFailure> failures)
{
}
