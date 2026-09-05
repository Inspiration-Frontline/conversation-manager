package ifl.agentbreaker.conversationmanager.services.rounds;

import ifl.agentbreaker.conversationmanager.domain.constants.ConversationRoundStatus;
import ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationRound;
import ifl.agentbreaker.conversationmanager.rpc.AppendConversationRoundProgressRequest;
import ifl.agentbreaker.conversationmanager.rpc.ConversationErrorCode;
import ifl.agentbreaker.conversationmanager.rpc.CreateConversationRoundCheckpointRequest;
import ifl.agentbreaker.conversationmanager.rpc.FinalizeConversationRoundRequest;
import ifl.agentbreaker.conversationmanager.rpc.RoundStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
class ConversationRoundProgressValidator
{
    /** Validates the identity and required fields of a new in-progress Round checkpoint.
     * @param request checkpoint mutation received from Runner
     */
    void validateCreate(CreateConversationRoundCheckpointRequest request)
    {
        validateMutationIdentity(request.getUserId(), request.getConversationId(), request.getRoundNumber(),
            request.getMutationId());

        if (!request.hasUserRequest() || request.getStartTime() <= 0 || !request.hasAgentIdentity()
            || !StringUtils.hasText(request.getTraceId()))
            throw invalid("Checkpoint request is incomplete.");
    }

    /** Validates that an append mutation contains at least one progress item.
     * @param request progress mutation received from Runner
     */
    void validateAppend(AppendConversationRoundProgressRequest request)
    {
        validateMutationIdentity(request.getUserId(), request.getConversationId(), request.getRoundNumber(), request.getMutationId());

        if (request.getTurnsCount() == 0 && request.getDispatchEvidenceCount() == 0)
            throw invalid("Progress mutation must append a Turn or dispatch evidence.");
    }

    /** Validates terminal status, timestamps, answer presence, and failure messages.
     * @param request finalization mutation received from Runner
     */
    void validateFinalize(FinalizeConversationRoundRequest request)
    {
        validateMutationIdentity(request.getUserId(), request.getConversationId(), request.getRoundNumber(), request.getMutationId());
        boolean completed = request.getStatus() == RoundStatus.ROUND_STATUS_COMPLETED;

        if (request.getEndTime() <= 0 || request.getStatus() == RoundStatus.ROUND_STATUS_IN_PROGRESS
            || request.getStatus() == RoundStatus.ROUND_STATUS_UNSPECIFIED
            || (completed && !request.hasFinalAnswer())
            || (!completed && request.hasFinalAnswer())
            || (request.getStatus() == RoundStatus.ROUND_STATUS_FAILED
                && !StringUtils.hasText(request.getErrorMessage())))
            throw error(ConversationErrorCode.CONVERSATION_ERROR_CODE_INVALID_ROUND_STATE,
                "Final Round state is invalid.");
    }

    /** Ensures an in-progress Round still has the revision the caller read.
     * @param round persisted Round being mutated
     * @param expectedRevision caller's optimistic-lock revision
     * @throws RoundPersistenceException when the Round is terminal or stale
     */
    void requireMutableRevision(ConversationRound round, long expectedRevision)
    {
        if (round.getStatus() != ConversationRoundStatus.IN_PROGRESS)
            throw error(ConversationErrorCode.CONVERSATION_ERROR_CODE_ROUND_NOT_IN_PROGRESS, "Round is already terminal.");

        if (round.getRevision() != expectedRevision)
            throw stale();
    }

    /** Creates the stable stale-revision failure returned to Runner.
     * @return stale revision exception
     */
    RoundPersistenceException stale()
    {
        return error(ConversationErrorCode.CONVERSATION_ERROR_CODE_STALE_REVISION, "expected_revision does not match the committed Round revision.");
    }

    /** Creates an invalid-request failure with a caller-safe message.
     * @param message validation message
     * @return invalid request exception
     */
    RoundPersistenceException invalid(String message)
    {
        return error(ConversationErrorCode.CONVERSATION_ERROR_CODE_INVALID_REQUEST, message);
    }

    /** Creates a classified Round persistence failure.
     * @param code protocol error code
     * @param message caller-safe explanation
     * @return persistence exception carrying the code and message
     */
    RoundPersistenceException error(ConversationErrorCode code, String message)
    {
        return new RoundPersistenceException(code.getNumber(), message);
    }

    /** Validates the identity shared by all checkpoint, append, and finalize mutations.
     * @param userId Trusted authenticated user identifier.
     * @param conversationId Stable public identifier of the Conversation.
     * @param roundNumber Numeric round number used for ordering or bounds.
     * @param mutationId Stable identifier of the mutation.
     */
    private void validateMutationIdentity(long userId, String conversationId, long roundNumber, String mutationId)
    {
        if (userId <= 0 || roundNumber <= 0 || !StringUtils.hasText(conversationId)
            || !StringUtils.hasText(mutationId) || mutationId.length() > 64)
            throw invalid("Mutation identity is invalid.");
    }
}
