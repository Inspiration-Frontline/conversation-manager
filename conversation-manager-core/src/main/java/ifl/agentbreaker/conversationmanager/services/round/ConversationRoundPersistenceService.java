package ifl.agentbreaker.conversationmanager.services.round;

import ifl.agentbreaker.conversationmanager.dao.*;
import ifl.agentbreaker.conversationmanager.domain.constants.ConversationRoundStatus;
import ifl.agentbreaker.conversationmanager.domain.constants.ConversationTurnStatus;
import ifl.agentbreaker.conversationmanager.domain.constants.LlmMessageRole;
import ifl.agentbreaker.conversationmanager.domain.constants.LlmMessageStorageMode;
import ifl.agentbreaker.conversationmanager.domain.entities.pg.*;
import ifl.agentbreaker.conversationmanager.rpc.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Date;

@Service
public class ConversationRoundPersistenceService
{
    @Autowired
    private ConversationMapper conversationMapper;

    @Autowired
    private ConversationRoundMapper conversationRoundMapper;

    @Autowired
    private ConversationTurnMapper conversationTurnMapper;

    @Autowired
    private ConversationLlmCallMapper conversationLlmCallMapper;

    @Autowired
    private ConversationLlmRequestMessageMapper conversationLlmRequestMessageMapper;

    @Autowired
    private ConversationRoundValidator conversationRoundValidator;

    @Autowired
    private ConversationRoundPayloadHasher conversationRoundPayloadHasher;

    @Autowired
    private ConversationMutationLock conversationMutationLock;

    @Autowired
    private TransactionTemplate transactionTemplate;

    /**
     * Validates, serializes, and atomically persists one complete Round.
     *
     * <p>The Redis mutation lock is deliberately acquired before the PostgreSQL transaction starts.
     * TransactionTemplate keeps that boundary explicit without requiring a separate command
     * service solely to trigger Spring's transactional proxy.</p>
     */
    public SaveConversationRoundRequest save(SaveConversationRoundRequest request)
    {
        conversationRoundValidator.validatePhaseFour(request);
        String payloadHash = conversationRoundPayloadHasher.hash(request);
        try (ConversationMutationLock.LockHandle ignored =
                 conversationMutationLock.acquire(request.getConversationId()))
        {
            SaveConversationRoundRequest savedRequest = transactionTemplate.execute(
                transactionStatus -> saveInTransaction(request, payloadHash));
            if (savedRequest == null)
                throw new IllegalStateException("Round persistence transaction returned no result.");
            return savedRequest;
        }
    }

    private SaveConversationRoundRequest saveInTransaction(SaveConversationRoundRequest request, String payloadHash)
    {
        Conversation conversation = conversationMapper.lockConversationByIdAndUser(
            request.getConversationId(), request.getUserId());
        if (conversation == null)
            throw error(ConversationErrorCode.CONVERSATION_ERROR_CODE_CONVERSATION_NOT_FOUND,
                "Conversation does not exist.");

        long highWater = conversation.getLatestRoundNumber();
        ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationRound existing =
            conversationRoundMapper.getRound(request.getConversationId(), request.getRoundNumber());
        if (request.getRoundNumber() <= highWater)
        {
            if (existing == null || existing.isDeleted())
                throw error(ConversationErrorCode.CONVERSATION_ERROR_CODE_ROUND_NUMBER_RETIRED,
                    "Round number has already been retired.");
            if (existing.getPayloadHashVersion() != ConversationRoundPayloadHasher.CURRENT_VERSION
                || !payloadHash.equals(existing.getPayloadHash()))
                throw error(ConversationErrorCode.CONVERSATION_ERROR_CODE_ROUND_NUMBER_CONFLICT,
                    "Round number already contains different persisted content.");
            return request;
        }

        if (request.getRoundNumber() != highWater + 1)
            throw error(ConversationErrorCode.CONVERSATION_ERROR_CODE_INVALID_REQUEST,
                "round_number must equal the persisted high-water mark plus one.");

        ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationRound savedRound =
            conversationRoundMapper.insertRound(toRound(request, payloadHash));
        if (savedRound == null)
            throw new IllegalStateException("Round insert returned no row.");

        if (request.getTurnsCount() == 1)
        {
            ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationTurn conversationTurn =
                conversationTurnMapper.insertTurn(toTurn(
                    request.getTurns(0), savedRound.getId(), request.getUserId()));
            if (conversationTurn == null)
                throw new IllegalStateException("Turn insert returned no row.");

            ConversationLlmCall conversationLlmCall = conversationLlmCallMapper.insertLlmCall(
                toLlmCall(request.getTurns(0).getLlmCall(), conversationTurn.getId(), request.getUserId()));
            if (conversationLlmCall == null)
                throw new IllegalStateException("LLM call insert returned no row.");

            // TODO: Replace repeated cross-Round FULL_SNAPSHOT rows with context_id plus the current
            // Round delta when the deferred Context checkpoint/compaction model is designed.
            int messageOrder = 0;
            for (LlmConversationMessage llmConversationMessage :
                request.getTurns(0).getLlmCall().getRequest().getMessagesList())
            {
                conversationLlmRequestMessageMapper.insertRequestMessage(toRequestMessage(
                    llmConversationMessage, conversationLlmCall.getId(), messageOrder++, request.getUserId()));
            }
        }

        if (conversationMapper.advanceLatestRoundNumber(request.getConversationId(), request.getUserId(),
            request.getRoundNumber()) != 1)
            throw new IllegalStateException("Failed to advance conversation round high-water mark.");

        return request;
    }

    private ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationRound toRound(
        SaveConversationRoundRequest request, String payloadHash)
    {
        ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationRound conversationRound =
            new ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationRound();
        applyAudit(conversationRound, request.getUserId());
        conversationRound.setConversationId(request.getConversationId());
        conversationRound.setRoundNumber(request.getRoundNumber());
        conversationRound.setUserRequestContent(request.getUserRequest().getContent());
        conversationRound.setFinalAnswerContent(request.hasFinalAnswer() ? request.getFinalAnswer().getContent() : null);
        conversationRound.setFinalSourceTurnNumber(
            request.hasFinalAnswer() ? request.getFinalAnswer().getSourceTurnNumber() : null);
        conversationRound.setStatus(switch (request.getStatus())
        {
            case ROUND_STATUS_COMPLETED -> ConversationRoundStatus.COMPLETED;
            case ROUND_STATUS_FAILED -> ConversationRoundStatus.FAILED;
            case ROUND_STATUS_CANCELLED -> ConversationRoundStatus.CANCELLED;
            default -> throw new IllegalArgumentException("Unsupported round status.");
        });
        conversationRound.setErrorMessage(request.getErrorMessage());
        conversationRound.setStartTime(new Date(request.getStartTime()));
        conversationRound.setEndTime(new Date(request.getEndTime()));
        conversationRound.setPayloadHashVersion(ConversationRoundPayloadHasher.CURRENT_VERSION);
        conversationRound.setPayloadHash(payloadHash);
        conversationRound.setDeleted(false);
        return conversationRound;
    }

    private ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationTurn toTurn(
        ifl.agentbreaker.conversationmanager.rpc.ConversationTurn source, long roundId, long userId)
    {
        ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationTurn conversationTurn =
            new ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationTurn();
        applyAudit(conversationTurn, userId);
        conversationTurn.setRoundId(roundId);
        conversationTurn.setTurnNumber(source.getTurnNumber());
        conversationTurn.setAgentId(source.getAgentIdentity().getAgentId());
        conversationTurn.setAgentName(source.getAgentIdentity().getName());
        conversationTurn.setAgentVersion(source.getAgentIdentity().getVersion());
        conversationTurn.setStatus(ConversationTurnStatus.COMPLETED);
        conversationTurn.setErrorMessage("");
        conversationTurn.setStartTime(new Date(source.getStartTime()));
        conversationTurn.setEndTime(new Date(source.getEndTime()));
        return conversationTurn;
    }

    private ConversationLlmCall toLlmCall(LlmCall source, long turnId, long userId)
    {
        LlmRequest llmRequest = source.getRequest();
        LlmResponse llmResponse = source.getResponse();
        ConversationLlmCall conversationLlmCall = new ConversationLlmCall();
        applyAudit(conversationLlmCall, userId);
        conversationLlmCall.setTurnId(turnId);
        conversationLlmCall.setProvider(llmRequest.getProvider());
        conversationLlmCall.setModel(llmRequest.getModel());
        conversationLlmCall.setRequestId(source.getRequestId());
        conversationLlmCall.setTraceId(source.getTraceId());
        conversationLlmCall.setMessageStorageMode(LlmMessageStorageMode.FULL_SNAPSHOT);
        conversationLlmCall.setToolChoicePresent(llmRequest.hasToolChoice());
        conversationLlmCall.setResponseFormat(
            llmRequest.getResponseFormat().isEmpty() ? null : llmRequest.getResponseFormat());
        conversationLlmCall.setTemperature(llmRequest.hasTemperature() ? llmRequest.getTemperature() : null);
        conversationLlmCall.setMaxOutputTokens(
            llmRequest.hasMaxOutputTokens() ? llmRequest.getMaxOutputTokens() : null);
        conversationLlmCall.setRawRequest(llmRequest.hasRawRequest() ? llmRequest.getRawRequest() : null);
        conversationLlmCall.setStartTime(new Date(source.getStartTime()));
        conversationLlmCall.setEndTime(new Date(source.getEndTime()));
        conversationLlmCall.setResponseMessagePresent(llmResponse.hasMessage());
        conversationLlmCall.setResponseContent(llmResponse.getMessage().getContent());
        conversationLlmCall.setFinishReason(llmResponse.getFinishReason());
        conversationLlmCall.setUsagePresent(llmResponse.hasUsage());
        if (llmResponse.hasUsage())
        {
            TokenUsage tokenUsage = llmResponse.getUsage();
            conversationLlmCall.setPromptTokens(tokenUsage.getPromptTokens());
            conversationLlmCall.setCompletionTokens(tokenUsage.getCompletionTokens());
            conversationLlmCall.setTotalTokens(tokenUsage.getTotalTokens());
            conversationLlmCall.setCachedPromptTokens(tokenUsage.getCachedPromptTokens());
            conversationLlmCall.setReasoningTokens(tokenUsage.getReasoningTokens());
        }
        conversationLlmCall.setRawResponse(
            llmResponse.hasRawResponse() ? llmResponse.getRawResponse() : null);
        conversationLlmCall.setResponseErrorMessage(llmResponse.getErrorMessage());
        conversationLlmCall.setReasoningContent(
            llmResponse.hasReasoningContent() ? llmResponse.getReasoningContent() : null);
        return conversationLlmCall;
    }

    private ConversationLlmRequestMessage toRequestMessage(LlmConversationMessage source, long llmCallId,
                                                            int messageOrder, long userId)
    {
        ConversationLlmRequestMessage conversationLlmRequestMessage = new ConversationLlmRequestMessage();
        applyAudit(conversationLlmRequestMessage, userId);
        conversationLlmRequestMessage.setLlmCallId(llmCallId);
        conversationLlmRequestMessage.setMessageOrder(messageOrder);
        conversationLlmRequestMessage.setRole(switch (source.getRole())
        {
            case MESSAGE_ROLE_SYSTEM -> LlmMessageRole.SYSTEM;
            case MESSAGE_ROLE_USER -> LlmMessageRole.USER;
            case MESSAGE_ROLE_ASSISTANT -> LlmMessageRole.ASSISTANT;
            case MESSAGE_ROLE_TOOL -> LlmMessageRole.TOOL;
            case MESSAGE_ROLE_DEVELOPER -> LlmMessageRole.DEVELOPER;
            default -> throw new IllegalArgumentException("Unsupported request message role.");
        });
        conversationLlmRequestMessage.setContent(source.getContent());
        conversationLlmRequestMessage.setToolCallId(
            source.getToolCallId().isEmpty() ? null : source.getToolCallId());
        return conversationLlmRequestMessage;
    }

    private void applyAudit(EntityBase entityBase, long userId)
    {
        entityBase.setCreatorId(userId);
        entityBase.setModifierId(userId);
    }

    private RoundPersistenceException error(ConversationErrorCode conversationErrorCode, String message)
    {
        return new RoundPersistenceException(conversationErrorCode.getNumber(), message);
    }
}
