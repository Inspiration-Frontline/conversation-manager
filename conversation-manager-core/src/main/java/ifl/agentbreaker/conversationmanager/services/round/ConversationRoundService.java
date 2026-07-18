package ifl.agentbreaker.conversationmanager.services.round;

import ifl.agentbreaker.conversationmanager.dao.ConversationLlmCallMapper;
import ifl.agentbreaker.conversationmanager.dao.ConversationLlmRequestMessageMapper;
import ifl.agentbreaker.conversationmanager.dao.ConversationLlmRequestMessageToolCallMapper;
import ifl.agentbreaker.conversationmanager.dao.ConversationLlmResponseToolCallMapper;
import ifl.agentbreaker.conversationmanager.dao.ConversationLlmToolDefinitionMapper;
import ifl.agentbreaker.conversationmanager.dao.ConversationMapper;
import ifl.agentbreaker.conversationmanager.dao.ConversationRoundMapper;
import ifl.agentbreaker.conversationmanager.dao.ConversationTurnMapper;
import ifl.agentbreaker.conversationmanager.dao.ConversationToolCallExecutionMapper;
import ifl.agentbreaker.conversationmanager.domain.constants.ConversationRoundStatus;
import ifl.agentbreaker.conversationmanager.domain.constants.ConversationTurnStatus;
import ifl.agentbreaker.conversationmanager.domain.constants.LlmMessageRole;
import ifl.agentbreaker.conversationmanager.domain.constants.LlmMessageStorageMode;
import ifl.agentbreaker.conversationmanager.domain.constants.ToolCallExecutionStatus;
import ifl.agentbreaker.conversationmanager.domain.constants.ToolSourceType;
import ifl.agentbreaker.conversationmanager.domain.dtos.responses.ConversationReplayResult;
import ifl.agentbreaker.conversationmanager.domain.dtos.responses.ConversationRoundHistoryResult;
import ifl.agentbreaker.conversationmanager.domain.dtos.responses.RoundHistoryView;
import ifl.agentbreaker.conversationmanager.domain.entities.pg.Conversation;
import ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationLlmCall;
import ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationLlmRequestMessage;
import ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationLlmRequestMessageToolCall;
import ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationLlmResponseToolCall;
import ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationLlmToolDefinition;
import ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationRound;
import ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationTurn;
import ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationToolCallExecution;
import ifl.agentbreaker.conversationmanager.domain.entities.pg.EntityBase;
import ifl.agentbreaker.conversationmanager.rpc.ConversationErrorCode;
import ifl.agentbreaker.conversationmanager.rpc.FunctionCall;
import ifl.agentbreaker.conversationmanager.rpc.MessageRole;
import ifl.agentbreaker.conversationmanager.rpc.LlmCall;
import ifl.agentbreaker.conversationmanager.rpc.LlmConversationMessage;
import ifl.agentbreaker.conversationmanager.rpc.LlmRequest;
import ifl.agentbreaker.conversationmanager.rpc.LlmResponse;
import ifl.agentbreaker.conversationmanager.rpc.SaveConversationRoundRequest;
import ifl.agentbreaker.conversationmanager.rpc.TokenUsage;
import ifl.agentbreaker.conversationmanager.rpc.ToolCall;
import ifl.agentbreaker.conversationmanager.rpc.ToolCallExecution;
import ifl.agentbreaker.conversationmanager.rpc.ToolDefinition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import stark.dataworks.boot.web.ServiceResponse;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ConversationRoundService
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
    private ConversationLlmRequestMessageToolCallMapper conversationLlmRequestMessageToolCallMapper;

    @Autowired
    private ConversationLlmToolDefinitionMapper conversationLlmToolDefinitionMapper;

    @Autowired
    private ConversationLlmResponseToolCallMapper conversationLlmResponseToolCallMapper;

    @Autowired
    private ConversationToolCallExecutionMapper conversationToolCallExecutionMapper;

    @Autowired
    private ConversationRoundValidator conversationRoundValidator;

    @Autowired
    private ConversationRoundPayloadHasher conversationRoundPayloadHasher;

    @Autowired
    private ConversationMutationLock conversationMutationLock;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private static final int ERROR_CONVERSATION_NOT_FOUND = 2002;

    public ConversationRoundHistoryResult getHistory(long userId, String conversationId)
    {
        Long latestRoundNumber = conversationMapper.getLatestRoundNumberByIdAndUser(conversationId, userId);
        if (latestRoundNumber == null)
            throw new RoundPersistenceException(ERROR_CONVERSATION_NOT_FOUND, "Conversation does not exist.");
        return new ConversationRoundHistoryResult(
            latestRoundNumber, conversationRoundMapper.listActiveRounds(conversationId));
    }

    public ServiceResponse<RoundHistoryView> getHttpHistory(long userId, String conversationId)
    {
        try
        {
            ConversationRoundHistoryResult history = getHistory(userId, conversationId);
            return ServiceResponse.buildSuccessResponse(new RoundHistoryView(
                conversationId,
                history.latestRoundNumber(),
                history.rounds().stream().map(round -> new RoundHistoryView.RoundView(
                    round.getRoundNumber(), round.getUserRequestContent(), round.getFinalAnswerContent(),
                    round.getStatus().name(), round.getErrorMessage(), round.getTurnCount(),
                    round.getStartTime().getTime(), round.getEndTime().getTime())).toList()));
        }
        catch (RoundPersistenceException e)
        {
            return ServiceResponse.buildErrorResponse(e.getCode(), e.getMessage());
        }
    }

    public ConversationReplayResult getModelContext(long userId, String conversationId, long endRoundNumber)
    {
        Long latestRoundNumber = conversationMapper.getLatestRoundNumberByIdAndUser(conversationId, userId);
        if (latestRoundNumber == null)
            throw new RoundPersistenceException(ERROR_CONVERSATION_NOT_FOUND, "Conversation does not exist.");
        if (endRoundNumber <= 0 || endRoundNumber > latestRoundNumber)
            throw new RoundPersistenceException(
                ConversationErrorCode.CONVERSATION_ERROR_CODE_INVALID_REQUEST_VALUE,
                "end_round_number must reference an assigned round.");

        ConversationRound boundaryRound = conversationRoundMapper.getRound(conversationId, endRoundNumber);
        if (boundaryRound == null || boundaryRound.isDeleted())
            throw new RoundPersistenceException(
                ConversationErrorCode.CONVERSATION_ERROR_CODE_ROUND_NOT_FOUND_VALUE,
                "Replay boundary round does not exist.");

        ConversationRound completedRound = conversationRoundMapper.getLatestCompletedRoundAtOrBefore(
            conversationId, endRoundNumber);
        if (completedRound == null)
            return new ConversationReplayResult(conversationId, List.of());

        ConversationTurn conversationTurn = conversationTurnMapper.getCompletedTurn(
            completedRound.getId(), completedRound.getFinalSourceTurnNumber());
        if (conversationTurn == null)
            throw new IllegalStateException("Completed replay round has no source turn.");
        ConversationLlmCall conversationLlmCall = conversationLlmCallMapper.getLlmCallByTurnId(
            conversationTurn.getId());
        if (conversationLlmCall == null || !conversationLlmCall.isResponseMessagePresent())
            throw new IllegalStateException("Completed replay turn has no LLM response.");

        Map<Long, List<ConversationLlmRequestMessageToolCall>> toolCallsByMessageId =
            conversationLlmRequestMessageToolCallMapper.listRequestMessageToolCallsForRound(completedRound.getId())
                .stream().collect(Collectors.groupingBy(
                    ConversationLlmRequestMessageToolCall::getRequestMessageId));
        List<LlmConversationMessage> contextMessages = conversationLlmRequestMessageMapper
            .listRequestMessagesForRound(completedRound.getId()).stream()
            .map(message -> toProtoMessage(message, toolCallsByMessageId.getOrDefault(message.getId(), List.of())))
            .collect(Collectors.toCollection(ArrayList::new));
        contextMessages.add(LlmConversationMessage.newBuilder()
            .setRole(MessageRole.MESSAGE_ROLE_ASSISTANT)
            .setContent(conversationLlmCall.getResponseContent())
            .build());
        return new ConversationReplayResult(conversationId, List.copyOf(contextMessages));
    }

    private LlmConversationMessage toProtoMessage(
        ConversationLlmRequestMessage message,
        List<ConversationLlmRequestMessageToolCall> toolCalls)
    {
        return LlmConversationMessage.newBuilder()
            .setRole(switch (message.getRole())
            {
                case SYSTEM -> MessageRole.MESSAGE_ROLE_SYSTEM;
                case USER -> MessageRole.MESSAGE_ROLE_USER;
                case ASSISTANT -> MessageRole.MESSAGE_ROLE_ASSISTANT;
                case TOOL -> MessageRole.MESSAGE_ROLE_TOOL;
                case DEVELOPER -> MessageRole.MESSAGE_ROLE_DEVELOPER;
            })
            .setContent(message.getContent() == null ? "" : message.getContent())
            .addAllToolCalls(toolCalls.stream().map(toolCall -> ToolCall.newBuilder()
                .setId(toolCall.getToolCallId())
                .setType(toolCall.getType())
                .setFunction(FunctionCall.newBuilder()
                    .setName(toolCall.getFunctionName())
                    .setArguments(toolCall.getArguments()))
                .build()).toList())
            .setToolCallId(message.getToolCallId() == null ? "" : message.getToolCallId())
            .build();
    }

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
        ConversationRound existing = conversationRoundMapper.getRound(
            request.getConversationId(), request.getRoundNumber());
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

        ConversationRound savedRound = conversationRoundMapper.insertRound(toRound(request, payloadHash));
        if (savedRound == null)
            throw new IllegalStateException("Round insert returned no row.");

        persistTurnsAndChildren(request, savedRound.getId());

        // TODO: Replace repeated cross-Round FULL_SNAPSHOT rows with context_id plus the current
        // Round delta when the deferred Context checkpoint/compaction model is designed.

        if (conversationMapper.advanceLatestRoundNumber(request.getConversationId(), request.getUserId(),
            request.getRoundNumber()) != 1)
            throw new IllegalStateException("Failed to advance conversation round high-water mark.");

        return request;
    }

    /**
     * Persists every Turn-owned table with one batch statement per table.
     *
     * <p>PostgreSQL does not contractually preserve input order for RETURNING rows. Associations
     * are therefore rebuilt from persisted business keys instead of list positions.</p>
     */
    private void persistTurnsAndChildren(SaveConversationRoundRequest request, long roundId)
    {
        if (request.getTurnsList().isEmpty())
            return;

        List<ConversationTurn> turns = new ArrayList<>();
        for (ifl.agentbreaker.conversationmanager.rpc.ConversationTurn sourceTurn : request.getTurnsList())
            turns.add(toTurn(sourceTurn, roundId, request.getUserId()));
        List<ConversationTurn> savedTurns = conversationTurnMapper.insertTurns(turns);
        requireReturnedRows("Turn", turns.size(), savedTurns);

        List<ConversationLlmCall> llmCalls = new ArrayList<>();
        for (ConversationTurn savedTurn : savedTurns)
        {
            int sourceIndex = Math.toIntExact(savedTurn.getTurnNumber() - 1);
            llmCalls.add(toLlmCall(
                request.getTurns(sourceIndex).getLlmCall(), savedTurn.getId(), request.getUserId()));
        }
        List<ConversationLlmCall> savedLlmCalls = conversationLlmCallMapper.insertLlmCalls(llmCalls);
        requireReturnedRows("LLM call", llmCalls.size(), savedLlmCalls);

        Map<Long, ConversationLlmCall> llmCallsByTurnId = savedLlmCalls.stream().collect(
            Collectors.toMap(ConversationLlmCall::getTurnId, llmCall -> llmCall));
        List<TurnPersistenceContext> contexts = new ArrayList<>();
        for (ConversationTurn savedTurn : savedTurns)
        {
            ConversationLlmCall savedLlmCall = llmCallsByTurnId.get(savedTurn.getId());
            if (savedLlmCall == null)
                throw new IllegalStateException("LLM call batch did not return a row for every Turn.");
            int sourceIndex = Math.toIntExact(savedTurn.getTurnNumber() - 1);
            contexts.add(new TurnPersistenceContext(
                request.getTurns(sourceIndex), savedTurn, savedLlmCall));
        }

        persistToolDefinitions(contexts, request.getUserId());
        persistRequestMessagesAndToolCalls(contexts, request.getUserId());
        persistResponseToolCallsAndExecutions(contexts, request.getUserId());
    }

    private ConversationRound toRound(
        SaveConversationRoundRequest request, String payloadHash)
    {
        ConversationRound conversationRound = new ConversationRound();
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

    private ConversationTurn toTurn( ifl.agentbreaker.conversationmanager.rpc.ConversationTurn source,
                                     long roundId, long userId)
    {
        ConversationTurn conversationTurn = new ConversationTurn();
        applyAudit(conversationTurn, userId);
        conversationTurn.setRoundId(roundId);
        conversationTurn.setTurnNumber(source.getTurnNumber());
        conversationTurn.setAgentId(source.getAgentIdentity().getAgentId());
        conversationTurn.setAgentName(source.getAgentIdentity().getName());
        conversationTurn.setAgentVersion(source.getAgentIdentity().getVersion());
        conversationTurn.setStatus(switch (source.getStatus())
        {
            case TURN_STATUS_COMPLETED -> ConversationTurnStatus.COMPLETED;
            case TURN_STATUS_FAILED -> ConversationTurnStatus.FAILED;
            case TURN_STATUS_CANCELLED -> ConversationTurnStatus.CANCELLED;
            default -> throw new IllegalArgumentException("Unsupported turn status.");
        });
        conversationTurn.setErrorMessage(source.getErrorMessage());
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
        conversationLlmCall.setMessageStorageMode(switch (llmRequest.getMessageStorageMode())
        {
            case LLM_MESSAGE_STORAGE_MODE_FULL_SNAPSHOT -> LlmMessageStorageMode.FULL_SNAPSHOT;
            case LLM_MESSAGE_STORAGE_MODE_APPEND_DELTA -> LlmMessageStorageMode.APPEND_DELTA;
            default -> throw new IllegalArgumentException("Unsupported LLM message storage mode.");
        });
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

    private void persistToolDefinitions(List<TurnPersistenceContext> contexts, long userId)
    {
        List<ConversationLlmToolDefinition> definitions = new ArrayList<>();
        for (TurnPersistenceContext context : contexts)
        {
            int toolOrder = 0;
            for (ToolDefinition source : context.sourceTurn().getLlmCall().getRequest().getToolsList())
            {
                ConversationLlmToolDefinition definition = new ConversationLlmToolDefinition();
                applyAudit(definition, userId);
                definition.setLlmCallId(context.llmCall().getId());
                definition.setToolOrder(toolOrder++);
                definition.setToolKey(source.getToolKey());
                definition.setToolName(source.getToolName());
                definition.setSourceType(switch (source.getSourceType())
                {
                    case TOOL_SOURCE_TYPE_INTERNAL -> ToolSourceType.INTERNAL;
                    case TOOL_SOURCE_TYPE_BUSINESS -> ToolSourceType.BUSINESS;
                    case TOOL_SOURCE_TYPE_MCP -> ToolSourceType.MCP;
                    default -> throw new IllegalArgumentException("Unsupported Tool source type.");
                });
                definition.setDescription(source.getDescription());
                definition.setParametersJson(source.getParametersJson());
                definition.setStrict(source.getStrict());
                definition.setDefinitionHash(source.getDefinitionHash());
                definitions.add(definition);
            }
        }
        if (!definitions.isEmpty())
            requireAffectedRows("Tool definition", definitions.size(),
                conversationLlmToolDefinitionMapper.insertToolDefinitions(definitions));
    }

    private void persistRequestMessagesAndToolCalls(List<TurnPersistenceContext> contexts, long userId)
    {
        List<ConversationLlmRequestMessage> messages = new ArrayList<>();
        Map<RequestMessageKey, LlmConversationMessage> sourceMessagesByKey = new HashMap<>();
        for (TurnPersistenceContext context : contexts)
        {
            int messageOrder = 0;
            for (LlmConversationMessage sourceMessage :
                context.sourceTurn().getLlmCall().getRequest().getMessagesList())
            {
                messages.add(toRequestMessage(
                    sourceMessage, context.llmCall().getId(), messageOrder, userId));
                sourceMessagesByKey.put(
                    new RequestMessageKey(context.llmCall().getId(), messageOrder), sourceMessage);
                messageOrder++;
            }
        }

        List<ConversationLlmRequestMessage> savedMessages =
            conversationLlmRequestMessageMapper.insertRequestMessages(messages);
        requireReturnedRows("LLM request message", messages.size(), savedMessages);

        List<ConversationLlmRequestMessageToolCall> requestToolCalls = new ArrayList<>();
        for (ConversationLlmRequestMessage savedMessage : savedMessages)
        {
            RequestMessageKey key = new RequestMessageKey(
                savedMessage.getLlmCallId(), savedMessage.getMessageOrder());
            LlmConversationMessage sourceMessage = sourceMessagesByKey.get(key);
            if (sourceMessage == null)
                throw new IllegalStateException("Request message batch returned an unknown logical row.");
            int callOrder = 0;
            for (ToolCall sourceToolCall : sourceMessage.getToolCallsList())
            {
                ConversationLlmRequestMessageToolCall toolCall =
                    new ConversationLlmRequestMessageToolCall();
                applyAudit(toolCall, userId);
                toolCall.setRequestMessageId(savedMessage.getId());
                toolCall.setCallOrder(callOrder++);
                toolCall.setToolCallId(sourceToolCall.getId());
                toolCall.setType(sourceToolCall.getType());
                toolCall.setFunctionName(sourceToolCall.getFunction().getName());
                toolCall.setArguments(sourceToolCall.getFunction().getArguments());
                requestToolCalls.add(toolCall);
            }
        }
        if (!requestToolCalls.isEmpty())
            requireAffectedRows("Request message Tool call", requestToolCalls.size(),
                conversationLlmRequestMessageToolCallMapper.insertRequestMessageToolCalls(requestToolCalls));
    }

    private void persistResponseToolCallsAndExecutions(
        List<TurnPersistenceContext> contexts, long userId)
    {
        List<ConversationLlmResponseToolCall> responseToolCalls = new ArrayList<>();
        Map<ResponseToolCallKey, ToolCallExecution> sourceExecutionsByKey = new HashMap<>();
        for (TurnPersistenceContext context : contexts)
        {
            for (ToolCallExecution execution : context.sourceTurn().getToolCallExecutionsList())
                sourceExecutionsByKey.put(
                    new ResponseToolCallKey(context.llmCall().getId(), execution.getToolCallId()), execution);

            int callOrder = 0;
            for (ToolCall sourceToolCall :
                context.sourceTurn().getLlmCall().getResponse().getMessage().getToolCallsList())
            {
                ConversationLlmResponseToolCall responseToolCall = new ConversationLlmResponseToolCall();
                applyAudit(responseToolCall, userId);
                responseToolCall.setTurnId(context.turn().getId());
                responseToolCall.setLlmCallId(context.llmCall().getId());
                responseToolCall.setCallOrder(callOrder++);
                responseToolCall.setToolCallId(sourceToolCall.getId());
                responseToolCall.setType(sourceToolCall.getType());
                responseToolCall.setFunctionName(sourceToolCall.getFunction().getName());
                responseToolCall.setArguments(sourceToolCall.getFunction().getArguments());
                responseToolCalls.add(responseToolCall);
            }
        }

        if (responseToolCalls.isEmpty())
            return;
        List<ConversationLlmResponseToolCall> savedToolCalls =
            conversationLlmResponseToolCallMapper.insertResponseToolCalls(responseToolCalls);
        requireReturnedRows("Response Tool call", responseToolCalls.size(), savedToolCalls);

        List<ConversationToolCallExecution> executions = new ArrayList<>();
        for (ConversationLlmResponseToolCall savedToolCall : savedToolCalls)
        {
            ResponseToolCallKey key = new ResponseToolCallKey(
                savedToolCall.getLlmCallId(), savedToolCall.getToolCallId());
            ToolCallExecution sourceExecution = sourceExecutionsByKey.get(key);
            if (sourceExecution == null)
                throw new IllegalStateException("Response Tool call batch returned an unknown logical row.");
            executions.add(toToolCallExecution(
                sourceExecution, savedToolCall.getTurnId(), savedToolCall.getId(),
                savedToolCall.getCallOrder(), userId));
        }
        requireAffectedRows("Tool execution", executions.size(),
            conversationToolCallExecutionMapper.insertToolCallExecutions(executions));
    }

    private ConversationToolCallExecution toToolCallExecution(
        ToolCallExecution source,
        long turnId,
        long responseToolCallId,
        int executionOrder,
        long userId)
    {
        ConversationToolCallExecution execution = new ConversationToolCallExecution();
        applyAudit(execution, userId);
        execution.setTurnId(turnId);
        execution.setResponseToolCallId(responseToolCallId);
        execution.setExecutionOrder(executionOrder);
        execution.setToolKey(source.getToolKey());
        execution.setStatus(switch (source.getStatus())
        {
            case TOOL_CALL_EXECUTION_STATUS_COMPLETED -> ToolCallExecutionStatus.COMPLETED;
            case TOOL_CALL_EXECUTION_STATUS_FAILED -> ToolCallExecutionStatus.FAILED;
            case TOOL_CALL_EXECUTION_STATUS_CANCELLED -> ToolCallExecutionStatus.CANCELLED;
            default -> throw new IllegalArgumentException("Unsupported Tool execution status.");
        });
        execution.setResultContent(source.getResultContent().isEmpty() ? null : source.getResultContent());
        execution.setRawResult(source.hasRawResult() ? source.getRawResult() : null);
        execution.setErrorMessage(source.getErrorMessage());
        execution.setStartTime(new Date(source.getStartTime()));
        execution.setEndTime(new Date(source.getEndTime()));
        return execution;
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

    private void requireReturnedRows(String label, int expected, List<?> rows)
    {
        if (rows == null || rows.size() != expected)
            throw new IllegalStateException(label + " batch returned an unexpected row count.");
    }

    private void requireAffectedRows(String label, int expected, int affectedRows)
    {
        if (affectedRows != expected)
            throw new IllegalStateException(label + " batch inserted an unexpected row count.");
    }

    private record TurnPersistenceContext(
        ifl.agentbreaker.conversationmanager.rpc.ConversationTurn sourceTurn,
        ConversationTurn turn,
        ConversationLlmCall llmCall)
    {
    }

    private record RequestMessageKey(long llmCallId, int messageOrder)
    {
    }

    private record ResponseToolCallKey(long llmCallId, String toolCallId)
    {
    }
}
