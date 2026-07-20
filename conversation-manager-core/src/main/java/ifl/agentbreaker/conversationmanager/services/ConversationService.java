package ifl.agentbreaker.conversationmanager.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import ifl.agentbreaker.authcenter.session.UserContextService;
import ifl.agentbreaker.conversationmanager.api.IConversationRpcService;
import ifl.agentbreaker.conversationmanager.api.dto.ContentPart;
import ifl.agentbreaker.conversationmanager.api.dto.ToolCall;
import ifl.agentbreaker.conversationmanager.api.dto.requests.DeleteMessagesRequest;
import ifl.agentbreaker.conversationmanager.api.dto.requests.UpdateTitleRequest;
import ifl.agentbreaker.conversationmanager.api.dto.responses.ConversationAbstract;
import ifl.agentbreaker.conversationmanager.api.dto.responses.ConversationMessageHistory;
import ifl.agentbreaker.conversationmanager.api.dto.responses.ConversationMessageInfo;
import ifl.agentbreaker.conversationmanager.dao.ConversationGroupRelationMapper;
import ifl.agentbreaker.conversationmanager.dao.ConversationMapper;
import ifl.agentbreaker.conversationmanager.dao.ConversationMessageMapper;
import ifl.agentbreaker.conversationmanager.dao.ConversationRoundMapper;
import ifl.agentbreaker.conversationmanager.dao.ConversationSharingMapper;
import ifl.agentbreaker.conversationmanager.services.files.ConversationFileService;
import ifl.agentbreaker.conversationmanager.domain.constants.ExportFormat;
import ifl.agentbreaker.conversationmanager.domain.constants.ShareExpiry;
import ifl.agentbreaker.conversationmanager.domain.dtos.requests.ExportConversationRequest;
import ifl.agentbreaker.conversationmanager.domain.dtos.requests.ForkConversationRequest;
import ifl.agentbreaker.conversationmanager.domain.dtos.requests.GetConversationsRequest;
import ifl.agentbreaker.conversationmanager.domain.dtos.requests.PinConversationRequest;
import ifl.agentbreaker.conversationmanager.domain.dtos.requests.ShareConversationRequest;
import ifl.agentbreaker.conversationmanager.domain.dtos.responses.ConversationSharingResult;
import ifl.agentbreaker.conversationmanager.domain.dtos.responses.ConversationShareSummary;
import ifl.agentbreaker.conversationmanager.domain.dtos.responses.SharedConversationView;
import ifl.agentbreaker.conversationmanager.domain.entities.pg.Conversation;
import ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationMessage;
import ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationSharing;
import ifl.agentbreaker.conversationmanager.support.BusinessIdManager;
import ifl.agentbreaker.conversationmanager.support.ConversationTitleManager;
import ifl.agentbreaker.conversationmanager.support.TextNormalizer;
import ifl.agentbreaker.conversationmanager.services.rounds.ConversationRoundService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import stark.dataworks.boot.autoconfig.web.LogArgumentsAndResponse;
import stark.dataworks.boot.web.PaginatedData;
import stark.dataworks.boot.web.ServiceResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Owns the HTTP and Dubbo lifecycle of user Conversations.
 *
 * <p>This service is the authorization boundary for Conversation metadata, legacy messages,
 * sharing, export, pinning, and deletion. It resolves the current user from the trusted session
 * context before every read or write and delegates file-reference cleanup to the file service.
 * Round/Turn persistence remains in {@code ConversationRoundService}, preventing the legacy
 * message projection from becoming a second execution-history source of truth.</p>
 */
@Slf4j
@Service
@DubboService
@Validated
@LogArgumentsAndResponse
public class ConversationService implements IConversationRpcService
{
    private static final int ERROR_CONVERSATION_NOT_FOUND = 2002;
    private static final int ERROR_SHARE_NOT_FOUND = 2003;
    private static final int ERROR_EXPORT_FAILED = 2004;
    private static final int ERROR_INVALID_CONVERSATION = 2005;

    private static final int DEFAULT_PAGE_INDEX = 1;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final TypeReference<List<ContentPart>> CONTENT_PARTS_TYPE = new TypeReference<>()
    {
    };
    private static final TypeReference<List<ToolCall>> TOOL_CALLS_TYPE = new TypeReference<>()
    {
    };

    @Autowired
    @Qualifier("conversationManagerObjectMapper")
    private ObjectMapper objectMapper;

    @Autowired
    private ConversationMapper conversationMapper;

    @Autowired
    private ConversationMessageMapper conversationMessageMapper;

    @Autowired
    private ConversationRoundMapper conversationRoundMapper;

    @Autowired
    private ConversationGroupRelationMapper conversationGroupRelationMapper;

    @Autowired
    private ConversationSharingMapper conversationSharingMapper;

    @Autowired
    private ConversationFileService conversationFileService;

    @Autowired
    private ConversationRoundService conversationRoundService;

    /**
     * Creates the durable Conversation shell used before the first message is sent.
     *
     * <p>The shell gives the browser a stable ID for streaming, refresh, and retry flows. The
     * first successfully persisted Round later replaces the default title.</p>
     *
     * @return newly created Conversation summary owned by the authenticated user
     */
    @Transactional(rollbackFor = Exception.class)
    public ServiceResponse<ConversationAbstract> createConversation()
    {
        long userId = UserContextService.getCurrentUserId();

        Conversation conversation = new Conversation();
        conversation.setCreatorId(userId);
        conversation.setModifierId(userId);
        conversation.setConversationId(BusinessIdManager.newConversationId());
        conversation.setTitle(ConversationTitleManager.DEFAULT_TITLE);
        conversation.setPinned(false);
        conversation.setDeleted(false);

        Conversation createdConversation = conversationMapper.insertConversation(conversation);
        return ServiceResponse.buildSuccessResponse(toConversationAbstract(createdConversation == null ? conversation : createdConversation));
    }

    /**
     * Returns one owned Conversation summary for direct navigation and refresh.
     *
     * @param conversationId stable public ID from the route
     * @return owned summary, or a not-found response that does not disclose another user's data
     */
    public ServiceResponse<ConversationAbstract> getConversationInfo(String conversationId)
    {
        long userId = UserContextService.getCurrentUserId();

        Conversation conversation = conversationMapper.getConversationByIdAndUser(conversationId, userId);
        if (conversation == null)
            return ServiceResponse.buildErrorResponse(ERROR_CONVERSATION_NOT_FOUND, "Conversation does not exist.");

        return ServiceResponse.buildSuccessResponse(toConversationAbstract(conversation));
    }

    /** Creates one independent authenticated share snapshot for an owned Conversation. */
    @Transactional(rollbackFor = Exception.class)
    public ServiceResponse<ConversationSharingResult> shareConversation(ShareConversationRequest request)
    {
        long userId = UserContextService.getCurrentUserId();

        if (!conversationMapper.existsByIdAndUser(request.getConversationId(), userId))
            return ServiceResponse.buildErrorResponse(ERROR_CONVERSATION_NOT_FOUND, "Conversation does not exist.");

        ShareExpiry expiry;
        try
        {
            expiry = ShareExpiry.parse(request.getExpiry());
        }
        catch (IllegalArgumentException e)
        {
            return ServiceResponse.buildErrorResponse(ERROR_INVALID_CONVERSATION,
                "Expiry must be ONE_DAY, SEVEN_DAYS, THIRTY_DAYS, or NEVER.");
        }

        ConversationSharing sharing = new ConversationSharing();
        sharing.setCreatorId(userId);
        sharing.setModifierId(userId);
        sharing.setParentConversationId(request.getConversationId());
        sharing.setSharedConversationId(BusinessIdManager.newConversationSharingId());
        sharing.setEndMessageId(conversationMessageMapper.getMaxMessageId(request.getConversationId()));
        sharing.setEndRoundNumber(conversationRoundService.getLatestCompletedRoundNumber(request.getConversationId()));
        sharing.setExpiresAt(expiry.getDuration() == null ? null : Instant.now().plus(expiry.getDuration()));
        conversationSharingMapper.insertConversationSharing(sharing);

        ConversationSharingResult result = new ConversationSharingResult();
        result.setParentConversationId(sharing.getParentConversationId());
        result.setSharedConversationId(sharing.getSharedConversationId());
        result.setEndRoundNumber(sharing.getEndRoundNumber());
        result.setExpiresAt(sharing.getExpiresAt());
        return ServiceResponse.buildSuccessResponse(result);
    }

    /** Returns one authenticated read-only snapshot through a valid share link. */
    public ServiceResponse<SharedConversationView> getSharedConversation(String sharedConversationId)
    {
        ConversationSharing sharing = conversationSharingMapper.getActiveConversationSharingBySharedId(sharedConversationId);
        if (sharing == null)
            return ServiceResponse.buildErrorResponse(ERROR_SHARE_NOT_FOUND, "Shared conversation does not exist or has expired.");
        Conversation source = conversationMapper.getConversationById(sharing.getParentConversationId());
        if (source == null || source.isDeleted())
            return ServiceResponse.buildErrorResponse(ERROR_CONVERSATION_NOT_FOUND, "Conversation does not exist.");
        return ServiceResponse.buildSuccessResponse(new SharedConversationView(
            source.getConversationId(), sharing.getSharedConversationId(), source.getTitle(), sharing.getExpiresAt(),
            conversationRoundService.getSharedHttpHistory(source.getConversationId(), sharing.getEndRoundNumber())));
    }

    /** Lists all share records belonging to the authenticated owner. */
    public ServiceResponse<List<ConversationShareSummary>> listConversationShares(String conversationId)
    {
        long userId = UserContextService.getCurrentUserId();
        if (!StringUtils.hasText(conversationId))
            return ServiceResponse.buildSuccessResponse(conversationSharingMapper.listAllConversationShareSummaries(userId));
        if (!conversationMapper.existsByIdAndUser(conversationId, userId))
            return ServiceResponse.buildErrorResponse(ERROR_CONVERSATION_NOT_FOUND, "Conversation does not exist.");
        Conversation parent = conversationMapper.getConversationByIdAndUser(conversationId, userId);
        List<ConversationShareSummary> result = conversationSharingMapper
            .listConversationSharingsByParentId(conversationId, userId).stream()
            .map(sharing -> new ConversationShareSummary(
                sharing.getParentConversationId(), sharing.getSharedConversationId(),
                parent == null ? "" : parent.getTitle(),
                sharing.getCreationTime(), sharing.getEndRoundNumber(),
                sharing.getExpiresAt(), sharing.isRevoked(), sharing.getRevokedAt()))
            .toList();
        return ServiceResponse.buildSuccessResponse(result);
    }

    /** Revokes one owner-created share without affecting other links for the Conversation. */
    @Transactional(rollbackFor = Exception.class)
    public ServiceResponse<Boolean> revokeConversationShare(String sharedConversationId)
    {
        long userId = UserContextService.getCurrentUserId();
        if (conversationSharingMapper.revokeConversationSharing(sharedConversationId, userId) <= 0)
            return ServiceResponse.buildErrorResponse(ERROR_SHARE_NOT_FOUND, "Shared conversation does not exist.");
        return ServiceResponse.buildSuccessResponse(true);
    }

    /**
     * Creates an owned fork containing the messages visible through a share record.
     *
     * <p>The source remains unchanged; the new Conversation receives a new business ID and audit
     * owner, so later edits or deletion do not cross ownership boundaries.</p>
     *
     * @param request shared Conversation ID and fork options
     * @return new owned Conversation summary, or an error when the share is unavailable
     */
    @Transactional(rollbackFor = Exception.class)
    public ServiceResponse<ConversationAbstract> forkConversation(@Valid ForkConversationRequest request)
    {
        long userId = UserContextService.getCurrentUserId();

        ConversationSharing sharing = conversationSharingMapper.getActiveConversationSharingBySharedId(request.getSharedConversationId());
        if (sharing == null)
            return ServiceResponse.buildErrorResponse(ERROR_SHARE_NOT_FOUND, "Shared conversation does not exist.");

        Conversation source = conversationMapper.getConversationById(sharing.getParentConversationId());
        if (source == null || source.isDeleted())
            return ServiceResponse.buildErrorResponse(ERROR_CONVERSATION_NOT_FOUND, "Conversation does not exist.");

        Conversation forked = new Conversation();
        forked.setCreatorId(userId);
        forked.setModifierId(userId);
        forked.setConversationId(BusinessIdManager.newConversationId());
        forked.setTitle(ConversationTitleManager.normalize(source.getTitle() + " Fork"));
        forked.setPinned(false);
        forked.setDeleted(false);
        Conversation createdConversation = conversationMapper.insertConversation(forked);
        conversationMessageMapper.forkConversationMessages(source.getConversationId(), forked.getConversationId(), userId, sharing.getEndMessageId());
        conversationRoundMapper.forkConversationHistory(
            source.getConversationId(), forked.getConversationId(), userId, sharing.getEndRoundNumber());
        conversationMapper.updateLatestRoundNumber(
            forked.getConversationId(), userId, sharing.getEndRoundNumber());
        return ServiceResponse.buildSuccessResponse(toConversationAbstract(createdConversation == null ? forked : createdConversation));
    }

    /**
     * Lists root-level owned Conversations for sidebar navigation.
     *
     * <p>Grouped Conversations are excluded by the mapper; page bounds and keyword whitespace are
     * normalized here so every caller receives deterministic pagination.</p>
     *
     * @param request page index, page size, and optional title keyword
     * @return owner-scoped page ordered according to the mapper's pin/time policy
     */
    public ServiceResponse<PaginatedData<ConversationAbstract>> getConversations(@Valid GetConversationsRequest request)
    {
        long userId = UserContextService.getCurrentUserId();

        int pageIndex = normalizePageIndex(request.getPageIndex());
        int pageSize = normalizePageSize(request.getPageSize());
        int offset = (pageIndex - 1) * pageSize;
        String keyword = TextNormalizer.trimToEmpty(request.getKeyword());

        long total = conversationMapper.countConversations(userId, keyword);
        List<Conversation> conversations = conversationMapper.listConversations(userId, keyword, pageSize, offset);
        List<ConversationAbstract> conversationAbstracts = conversations.stream()
            .map(this::toConversationAbstract)
            .toList();

        PaginatedData<ConversationAbstract> page = new PaginatedData<>();
        page.setCurrent(pageIndex);
        page.setPageSize(pageSize);
        page.setTotal(total);
        page.setPageCount((total + pageSize - 1) / pageSize);
        page.setData(conversationAbstracts);
        return ServiceResponse.buildSuccessResponse(page);
    }

    /**
     * Returns the legacy flat-message projection for one owned Conversation.
     *
     * @param conversationId stable public ID used for ownership lookup
     * @return ordered legacy messages, or a not-found response
     */
    public ServiceResponse<ConversationMessageHistory> getConversationMessageHistory(String conversationId)
    {
        long userId = UserContextService.getCurrentUserId();

        Conversation conversation = conversationMapper.getConversationByIdAndUser(conversationId, userId);
        if (conversation == null)
            return ServiceResponse.buildErrorResponse(ERROR_CONVERSATION_NOT_FOUND, "Conversation does not exist.");

        return ServiceResponse.buildSuccessResponse(buildConversationMessageHistory(conversation, null));
    }

    /**
     * Returns only export formats backed by a concrete renderer in this service.
     *
     * @return enum names accepted by {@link #exportConversation}
     */
    public ServiceResponse<List<String>> getAcceptableExportFormats()
    {
        List<String> exportFormats = new ArrayList<>();
        for (ExportFormat exportFormat : ExportFormat.values())
            exportFormats.add(exportFormat.name());

        return ServiceResponse.buildSuccessResponse(exportFormats);
    }

    /**
     * Streams a requested Conversation export without buffering it in the browser.
     *
     * <p>Ownership is checked before rendering. Serialization failures are converted to an HTTP
     * error because this method writes directly to the servlet response.</p>
     *
     * @param request owned Conversation ID and implemented export format
     * @param response servlet response receiving content type, headers, and bytes
     */
    public void exportConversation(@Valid ExportConversationRequest request, HttpServletResponse response)
    {
        long userId = UserContextService.getCurrentUserId();

        Conversation conversation = conversationMapper.getConversationByIdAndUser(request.getConversationId(), userId);
        if (conversation == null)
        {
            sendError(response, HttpServletResponse.SC_NOT_FOUND, "Conversation does not exist.");
            return;
        }

        try
        {
            ConversationMessageHistory history = buildConversationMessageHistory(conversation, null);
            ExportPayload payload = buildExportPayload(history, request.getExportFormat());
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType(payload.contentType());
            response.setHeader("Content-Disposition", "attachment; filename=\"" + payload.filename() + "\"");
            response.getOutputStream().write(payload.content().getBytes(StandardCharsets.UTF_8));
        }
        catch (Exception e)
        {
            log.warn("Failed to export conversation {}.", request.getConversationId(), e);
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to export conversation.");
        }
    }

    /**
     * Updates an owned Conversation title through the shared title-normalization policy.
     *
     * @param request owned Conversation ID and replacement title
     * @return updated summary, or a not-found response
     */
    @Transactional(rollbackFor = Exception.class)
    public ServiceResponse<ConversationAbstract> updateTitle(@Valid UpdateTitleRequest request)
    {
        long userId = UserContextService.getCurrentUserId();

        String title = ConversationTitleManager.normalize(request.getTitle());
        Conversation conversation = conversationMapper.updateConversationTitle(request.getConversationId(), userId, title);
        if (conversation == null)
            return ServiceResponse.buildErrorResponse(ERROR_CONVERSATION_NOT_FOUND, "Conversation does not exist.");

        return ServiceResponse.buildSuccessResponse(toConversationAbstract(conversation));
    }

    /**
     * Soft-deletes one owned Conversation and releases relation and file references.
     *
     * @param conversationId stable public ID selected by the caller
     * @return success when the Conversation existed and all cleanup committed
     */
    @Transactional(rollbackFor = Exception.class)
    public ServiceResponse<Boolean> deleteConversation(String conversationId)
    {
        long userId = UserContextService.getCurrentUserId();
        if (!deleteSingleConversation(conversationId, userId))
            return ServiceResponse.buildErrorResponse(ERROR_CONVERSATION_NOT_FOUND, "Conversation does not exist.");
        return ServiceResponse.buildSuccessResponse(true);
    }

    /**
     * Performs the single-Conversation cleanup contract after ownership-scoped deletion.
     *
     * @param conversationId stable public ID to delete
     * @param userId authenticated owner applied to every mapper predicate
     * @return {@code true} when the Conversation row was transitioned, otherwise {@code false}
     */
    private boolean deleteSingleConversation(String conversationId, long userId)
    {
        int updated = conversationMapper.deleteConversation(conversationId, userId);
        if (updated <= 0)
            return false;
        conversationSharingMapper.revokeByParentConversationIds(List.of(conversationId), userId);
        conversationGroupRelationMapper.deleteConversationGroupRelationsByConversationId(conversationId, userId);
        conversationFileService.releaseConversationReferences(conversationId, userId);
        return true;
    }

    /**
     * Soft-deletes an owned Conversation batch and performs set-based relation cleanup.
     *
     * <p>Relations and file references are released in batches so deleting many Conversations does
     * not create nested per-Conversation database loops.</p>
     *
     * @param conversationIds candidate owned Conversation IDs; blanks and duplicates are removed
     * @return success after logical deletion and reference cleanup commit
     */
    @Transactional(rollbackFor = Exception.class)
    public ServiceResponse<Boolean> deleteConversations(List<String> conversationIds)
    {
        if (conversationIds == null || conversationIds.isEmpty())
            return ServiceResponse.buildErrorResponse(ERROR_CONVERSATION_NOT_FOUND, "At least one conversation is required.");
        long userId = UserContextService.getCurrentUserId();
        List<String> uniqueIds = BusinessIdManager.normalizeIds(conversationIds);
        if (uniqueIds.isEmpty() || !conversationMapper.allOwnedConversationsExist(userId, uniqueIds))
            return ServiceResponse.buildErrorResponse(ERROR_CONVERSATION_NOT_FOUND, "Conversation does not exist.");
        conversationMapper.deleteConversations(uniqueIds, userId);
        conversationSharingMapper.revokeByParentConversationIds(uniqueIds, userId);
        conversationGroupRelationMapper.deleteConversationGroupRelationsByConversationIds(uniqueIds, userId);
        conversationFileService.releaseConversationReferences(uniqueIds, userId);
        return ServiceResponse.buildSuccessResponse(true);
    }

    /**
     * Deletes selected legacy messages after verifying ownership of their parent Conversation.
     *
     * @param request parent Conversation ID and message IDs selected by the caller
     * @return success after the owner-scoped delete completes
     */
    @Transactional(rollbackFor = Exception.class)
    public ServiceResponse<Boolean> deleteMessages(DeleteMessagesRequest request)
    {
        long userId = UserContextService.getCurrentUserId();

        if (!conversationMapper.existsByIdAndUser(request.getConversationId(), userId))
            return ServiceResponse.buildErrorResponse(ERROR_CONVERSATION_NOT_FOUND, "Conversation does not exist.");

        conversationMessageMapper.deleteMessages(request.getConversationId(), userId, request.getMessageIds());
        return ServiceResponse.buildSuccessResponse(true);
    }

    /**
     * Pins or unpins root-level owned Conversations as one set-based operation.
     *
     * <p>Grouped Conversations cannot be pinned because pinning is a property of root navigation.
     * An empty ID list combined with unpin clears all pins for the current user.</p>
     *
     * @param request target IDs and desired pin state
     * @return success after validation and update
     */
    @Transactional(rollbackFor = Exception.class)
    public ServiceResponse<Boolean> pinConversation(@Valid PinConversationRequest request)
    {
        long userId = UserContextService.getCurrentUserId();

        List<String> conversationIds = getPinConversationIds(request);
        boolean pinned = request == null || request.getPinned() == null || request.getPinned();

        if (CollectionUtils.isEmpty(conversationIds))
        {
            if (!pinned)
                conversationMapper.clearConversationPinned(userId);
            return ServiceResponse.buildSuccessResponse(true);
        }

        if (!conversationMapper.allOwnedConversationsExist(userId, conversationIds))
            return ServiceResponse.buildErrorResponse(ERROR_INVALID_CONVERSATION, "Some conversations do not exist.");

        if (pinned && !conversationMapper.allOwnedUngroupedConversationsExist(userId, conversationIds))
            return ServiceResponse.buildErrorResponse(ERROR_INVALID_CONVERSATION, "Grouped conversations cannot be pinned.");

        conversationMapper.updateConversationPinnedByIds(userId, conversationIds, pinned);

        return ServiceResponse.buildSuccessResponse(true);
    }

    /**
     * Normalizes optional pin IDs while preserving the empty-list unpin-all behavior.
     *
     * @param request nullable pin command
     * @return ordered, unique, non-blank Conversation IDs
     */
    private List<String> getPinConversationIds(PinConversationRequest request)
    {
        if (request == null)
            return new ArrayList<>();

        return BusinessIdManager.normalizeIds(request.getConversationIds());
    }

    /**
     * Builds a legacy message-history DTO up to an optional share snapshot boundary.
     *
     * @param conversation authorized parent entity
     * @param endMessageId inclusive upper message ID, or {@code null} for current history
     * @return ordered public history projection
     */
    private ConversationMessageHistory buildConversationMessageHistory(Conversation conversation, Long endMessageId)
    {
        List<ConversationMessage> messages = conversationMessageMapper.listConversationMessages(conversation.getConversationId(), endMessageId);
        List<ConversationMessageInfo> messageInfos = messages.stream()
            .map(this::toConversationMessageInfo)
            .toList();

        ConversationMessageHistory history = new ConversationMessageHistory();
        history.setConversationId(conversation.getConversationId());
        history.setTitle(conversation.getTitle());
        history.setMessages(messageInfos);
        return history;
    }

    /**
     * Maps a persisted message and deserializes its structured JSON fields for HTTP clients.
     *
     * @param message persisted legacy message row
     * @return public message projection
     */
    private ConversationMessageInfo toConversationMessageInfo(ConversationMessage message)
    {
        ConversationMessageInfo messageInfo = new ConversationMessageInfo();
        BeanUtils.copyProperties(message, messageInfo, "contentParts", "toolCalls");
        messageInfo.setContentParts(readJsonList(message.getContentParts(), CONTENT_PARTS_TYPE));
        messageInfo.setToolCalls(readJsonList(message.getToolCalls(), TOOL_CALLS_TYPE));
        return messageInfo;
    }

    /**
     * Deserializes an optional JSON array without failing the entire history projection.
     *
     * @param json nullable persisted JSON text
     * @param typeReference concrete list element type retained for Jackson
     * @param <T> structured content or tool-call element type
     * @return parsed list, {@code null} when absent, or an empty list when malformed
     */
    private <T> List<T> readJsonList(String json, TypeReference<List<T>> typeReference)
    {
        if (!StringUtils.hasText(json))
            return null;

        try
        {
            return objectMapper.readValue(json, typeReference);
        }
        catch (Exception e)
        {
            log.warn("Failed to parse conversation message JSON.", e);
            return Collections.emptyList();
        }
    }

    /**
     * Maps the persistence entity into the stable sidebar/navigation summary.
     *
     * @param conversation authorized persistence entity
     * @return public summary containing identity, title, pin state, and timestamps
     */
    private ConversationAbstract toConversationAbstract(Conversation conversation)
    {
        ConversationAbstract conversationAbstract = new ConversationAbstract();
        BeanUtils.copyProperties(conversation, conversationAbstract);
        return conversationAbstract;
    }

    /**
     * Renders one history projection into the selected downloadable export format.
     *
     * @param history authorized history projection
     * @param exportFormat implemented renderer selection
     * @return filename, media type, and serialized content
     * @throws IOException when JSON serialization fails
     */
    private ExportPayload buildExportPayload(ConversationMessageHistory history, ExportFormat exportFormat) throws IOException
    {
        String baseFilename = history.getConversationId() + "." + exportFormat.name().toLowerCase(Locale.ROOT);
        return switch (exportFormat)
        {
            case JSON -> new ExportPayload(baseFilename, MediaType.APPLICATION_JSON_VALUE, objectMapper.writeValueAsString(history));
            case HTML -> new ExportPayload(baseFilename, MediaType.TEXT_HTML_VALUE, toHtml(history));
            case MARKDOWN -> new ExportPayload(baseFilename, "text/markdown;charset=UTF-8", toMarkdown(history));
            case TXT -> new ExportPayload(baseFilename, MediaType.TEXT_PLAIN_VALUE, toPlainText(history));
        };
    }

    /**
     * Renders conversation history as portable role-prefixed plain text.
     *
     * @param history authorized history projection
     * @return UTF-8-safe text content
     */
    private String toPlainText(ConversationMessageHistory history)
    {
        StringBuilder builder = new StringBuilder();
        builder.append(history.getTitle()).append(System.lineSeparator()).append(System.lineSeparator());
        for (ConversationMessageInfo message : history.getMessages())
            builder.append(message.getRole()).append(": ").append(TextNormalizer.trimToEmpty(message.getContent())).append(System.lineSeparator());

        return builder.toString();
    }

    /**
     * Renders conversation history as Markdown headings and message bodies.
     *
     * @param history authorized history projection
     * @return Markdown document text
     */
    private String toMarkdown(ConversationMessageHistory history)
    {
        StringBuilder builder = new StringBuilder();
        builder.append("# ").append(history.getTitle()).append(System.lineSeparator()).append(System.lineSeparator());
        for (ConversationMessageInfo message : history.getMessages())
            builder.append("## ").append(message.getRole()).append(System.lineSeparator()).append(TextNormalizer.trimToEmpty(message.getContent())).append(System.lineSeparator()).append(System.lineSeparator());

        return builder.toString();
    }

    /**
     * Renders conversation history as escaped standalone HTML.
     *
     * @param history authorized history projection
     * @return complete HTML document with untrusted text escaped
     */
    private String toHtml(ConversationMessageHistory history)
    {
        StringBuilder builder = new StringBuilder();
        builder.append("<!doctype html><html><head><meta charset=\"utf-8\"><title>")
            .append(escapeHtml(history.getTitle()))
            .append("</title></head><body><h1>")
            .append(escapeHtml(history.getTitle()))
            .append("</h1>");
        for (ConversationMessageInfo message : history.getMessages())
        {
            builder.append("<section><h2>")
                .append(message.getRole())
                .append("</h2><p>")
                .append(escapeHtml(TextNormalizer.trimToEmpty(message.getContent())))
                .append("</p></section>");
        }
        builder.append("</body></html>");
        return builder.toString();
    }

    /**
     * Writes an HTTP error when export lookup or rendering cannot produce a file.
     *
     * @param response servlet response to complete
     * @param status HTTP status code
     * @param message client-safe failure message
     */
    private void sendError(HttpServletResponse response, int status, String message)
    {
        try
        {
            response.sendError(status, message);
        }
        catch (IOException e)
        {
            log.warn("Failed to write error response.", e);
        }
    }

    /**
     * Escapes untrusted conversation text before embedding it in exported HTML.
     *
     * @param value nullable title or message content
     * @return HTML-safe text, or an empty string for {@code null}
     */
    private static String escapeHtml(String value)
    {
        if (value == null)
            return "";

        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;");
    }

    /**
     * Clamps an invalid page index to the first page.
     *
     * @param pageIndex caller-supplied one-based page index
     * @return positive one-based page index
     */
    private static int normalizePageIndex(int pageIndex)
    {
        return pageIndex <= 0 ? DEFAULT_PAGE_INDEX : pageIndex;
    }

    /**
     * Applies the service page-size default and upper bound.
     *
     * @param pageSize caller-supplied page size
     * @return bounded positive page size
     */
    private static int normalizePageSize(int pageSize)
    {
        if (pageSize <= 0)
            return DEFAULT_PAGE_SIZE;

        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    /**
     * Carries renderer output to the servlet-writing boundary without exposing format branches to
     * the controller.
     *
     * @param filename download filename derived from the Conversation ID and format
     * @param contentType HTTP media type
     * @param content serialized export body
     */
    private record ExportPayload(String filename, String contentType, String content)
    {
    }
}
