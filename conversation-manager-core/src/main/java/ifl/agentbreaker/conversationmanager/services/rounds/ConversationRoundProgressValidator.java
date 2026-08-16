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
    void validateCreate(CreateConversationRoundCheckpointRequest request)
    {
        validateMutationIdentity(request.getUserId(), request.getConversationId(), request.getRoundNumber(),
            request.getMutationId());
        if (!request.hasUserRequest() || request.getStartTime() <= 0 || !request.hasAgentIdentity()
            || !StringUtils.hasText(request.getTraceId()))
            throw invalid("Checkpoint request is incomplete.");
    }

    void validateAppend(AppendConversationRoundProgressRequest request)
    {
        validateMutationIdentity(request.getUserId(), request.getConversationId(), request.getRoundNumber(),
            request.getMutationId());
        if (request.getTurnsCount() == 0 && request.getDispatchEvidenceCount() == 0)
            throw invalid("Progress mutation must append a Turn or dispatch evidence.");
    }

    void validateFinalize(FinalizeConversationRoundRequest request)
    {
        validateMutationIdentity(request.getUserId(), request.getConversationId(), request.getRoundNumber(),
            request.getMutationId());
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

    void requireMutableRevision(ConversationRound round, long expectedRevision)
    {
        if (round.getStatus() != ConversationRoundStatus.IN_PROGRESS)
            throw error(ConversationErrorCode.CONVERSATION_ERROR_CODE_ROUND_NOT_IN_PROGRESS,
                "Round is already terminal.");
        if (round.getRevision() != expectedRevision)
            throw stale();
    }

    RoundPersistenceException stale()
    {
        return error(ConversationErrorCode.CONVERSATION_ERROR_CODE_STALE_REVISION,
            "expected_revision does not match the committed Round revision.");
    }

    RoundPersistenceException invalid(String message)
    {
        return error(ConversationErrorCode.CONVERSATION_ERROR_CODE_INVALID_REQUEST, message);
    }

    RoundPersistenceException error(ConversationErrorCode code, String message)
    {
        return new RoundPersistenceException(code.getNumber(), message);
    }

    private void validateMutationIdentity(long userId, String conversationId, long roundNumber, String mutationId)
    {
        if (userId <= 0 || roundNumber <= 0 || !StringUtils.hasText(conversationId)
            || !StringUtils.hasText(mutationId) || mutationId.length() > 64)
            throw invalid("Mutation identity is invalid.");
    }
}
