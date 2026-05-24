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
import ifl.agentbreaker.conversationmanager.dao.ConversationSharingMapper;
import ifl.agentbreaker.conversationmanager.domain.constants.ExportFormat;
import ifl.agentbreaker.conversationmanager.domain.dtos.ConversationPinOrder;
import ifl.agentbreaker.conversationmanager.domain.dtos.requests.ExportConversationRequest;
import ifl.agentbreaker.conversationmanager.domain.dtos.requests.ForkConversationRequest;
import ifl.agentbreaker.conversationmanager.domain.dtos.requests.GetConversationsRequest;
import ifl.agentbreaker.conversationmanager.domain.dtos.requests.PinConversationRequest;
import ifl.agentbreaker.conversationmanager.domain.dtos.requests.ShareConversationRequest;
import ifl.agentbreaker.conversationmanager.domain.dtos.responses.ConversationSharingResult;
import ifl.agentbreaker.conversationmanager.domain.entities.pg.Conversation;
import ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationGroupRelation;
import ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationMessage;
import ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationSharing;
import ifl.agentbreaker.conversationmanager.support.BusinessIdManager;
import ifl.agentbreaker.conversationmanager.support.TextNormalizer;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
    private static final int MAX_TITLE_LENGTH = 200;
    private static final String DEFAULT_TITLE = "New Conversation";

    private static final TypeReference<List<ContentPart>> CONTENT_PARTS_TYPE = new TypeReference<>()
    {
    };
    private static final TypeReference<List<ToolCall>> TOOL_CALLS_TYPE = new TypeReference<>()
    {
    };

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private ConversationMapper conversationMapper;

    @Autowired
    private ConversationMessageMapper conversationMessageMapper;

    @Autowired
    private ConversationGroupRelationMapper conversationGroupRelationMapper;

    @Autowired
    private ConversationSharingMapper conversationSharingMapper;

    /**
     * Create a new conversation.
     * @return The created conversation.
     */
    @Transactional(rollbackFor = Exception.class)
    public ServiceResponse<ConversationAbstract> createConversation()
    {
        long userId = UserContextService.getCurrentUserId();

        Conversation conversation = new Conversation();
        conversation.setCreatorId(userId);
        conversation.setModifierId(userId);
        conversation.setConversationId(BusinessIdManager.newConversationId());
        conversation.setTitle(DEFAULT_TITLE);
        conversation.setDeleted(false);

        conversationMapper.insertConversation(conversation);
        Conversation createdConversation = conversationMapper.getConversationByIdAndUser(conversation.getConversationId(), userId);
        return ServiceResponse.buildSuccessResponse(toConversationAbstract(createdConversation == null ? conversation : createdConversation));
    }

    public ServiceResponse<ConversationAbstract> getConversationInfo(String conversationId)
    {
        long userId = UserContextService.getCurrentUserId();

        Conversation conversation = conversationMapper.getConversationByIdAndUser(conversationId, userId);
        if (conversation == null)
            return ServiceResponse.buildErrorResponse(ERROR_CONVERSATION_NOT_FOUND, "Conversation does not exist.");

        return ServiceResponse.buildSuccessResponse(toConversationAbstract(conversation));
    }

    @Transactional(rollbackFor = Exception.class)
    public ServiceResponse<ConversationSharingResult> shareConversation(ShareConversationRequest request)
    {
        long userId = UserContextService.getCurrentUserId();

        Conversation conversation = conversationMapper.getConversationByIdAndUser(request.getConversationId(), userId);
        if (conversation == null)
            return ServiceResponse.buildErrorResponse(ERROR_CONVERSATION_NOT_FOUND, "Conversation does not exist.");

        ConversationSharing sharing = new ConversationSharing();
        sharing.setCreatorId(userId);
        sharing.setModifierId(userId);
        sharing.setParentConversationId(request.getConversationId());
        sharing.setSharedConversationId(BusinessIdManager.newConversationSharingId());
        sharing.setEndMessageId(conversationMessageMapper.getMaxMessageId(request.getConversationId()));
        sharing.setAccessibleAfterDeleted(request.isAccessibleAfterDeleted());
        conversationSharingMapper.insertConversationSharing(sharing);

        ConversationSharingResult result = new ConversationSharingResult();
        result.setParentConversationId(sharing.getParentConversationId());
        result.setSharedConversationId(sharing.getSharedConversationId());
        return ServiceResponse.buildSuccessResponse(result);
    }

    /**
     * Forks a conversation from a shared conversation.
     * @return The forked conversation.
     */
    @Transactional(rollbackFor = Exception.class)
    public ServiceResponse<ConversationAbstract> forkConversation(@Valid ForkConversationRequest request)
    {
        long userId = UserContextService.getCurrentUserId();

        ConversationSharing sharing = conversationSharingMapper.getConversationSharingBySharedId(request.getSharedConversationId());
        if (sharing == null)
            return ServiceResponse.buildErrorResponse(ERROR_SHARE_NOT_FOUND, "Shared conversation does not exist.");

        Conversation source = conversationMapper.getConversationById(sharing.getParentConversationId());
        if (source == null || (source.isDeleted() && !sharing.isAccessibleAfterDeleted()))
            return ServiceResponse.buildErrorResponse(ERROR_CONVERSATION_NOT_FOUND, "Conversation does not exist.");

        Conversation forked = new Conversation();
        forked.setCreatorId(userId);
        forked.setModifierId(userId);
        forked.setConversationId(BusinessIdManager.newConversationId());
        forked.setTitle(TextNormalizer.trimToMaxLength(source.getTitle() + " Fork", MAX_TITLE_LENGTH));
        forked.setDeleted(false);
        conversationMapper.insertConversation(forked);

        List<ConversationMessage> sourceMessages = conversationMessageMapper.listConversationMessages(source.getConversationId(), sharing.getEndMessageId());
        if (!CollectionUtils.isEmpty(sourceMessages))
        {
            List<ConversationMessage> forkedMessages = new ArrayList<>(sourceMessages.size());
            for (ConversationMessage sourceMessage : sourceMessages)
                forkedMessages.add(copyMessageForConversation(sourceMessage, forked.getConversationId(), userId));
            conversationMessageMapper.insertMessages(forkedMessages);
        }

        Conversation createdConversation = conversationMapper.getConversationByIdAndUser(forked.getConversationId(), userId);
        return ServiceResponse.buildSuccessResponse(toConversationAbstract(createdConversation == null ? forked : createdConversation));
    }

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

    public ServiceResponse<ConversationMessageHistory> getConversationMessageHistory(String conversationId)
    {
        long userId = UserContextService.getCurrentUserId();

        Conversation conversation = conversationMapper.getConversationByIdAndUser(conversationId, userId);
        if (conversation == null)
            return ServiceResponse.buildErrorResponse(ERROR_CONVERSATION_NOT_FOUND, "Conversation does not exist.");

        return ServiceResponse.buildSuccessResponse(buildConversationMessageHistory(conversation, null));
    }

    public ServiceResponse<List<String>> getAcceptableExportFormats()
    {
        List<String> exportFormats = new ArrayList<>();
        for (ExportFormat exportFormat : ExportFormat.values())
            exportFormats.add(exportFormat.name());

        return ServiceResponse.buildSuccessResponse(exportFormats);
    }

    /**
     * Exports a conversation to a file with the specified format.
     * @param request The export request.
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

    @Transactional(rollbackFor = Exception.class)
    public ServiceResponse<ConversationAbstract> updateTitle(@Valid UpdateTitleRequest request)
    {
        long userId = UserContextService.getCurrentUserId();

        String title = TextNormalizer.trimToMaxLength(request.getTitle(), MAX_TITLE_LENGTH);
        int updated = conversationMapper.updateConversationTitle(request.getConversationId(), userId, title);
        if (updated <= 0)
            return ServiceResponse.buildErrorResponse(ERROR_CONVERSATION_NOT_FOUND, "Conversation does not exist.");

        Conversation conversation = conversationMapper.getConversationByIdAndUser(request.getConversationId(), userId);
        return ServiceResponse.buildSuccessResponse(toConversationAbstract(conversation));
    }

    @Transactional(rollbackFor = Exception.class)
    public ServiceResponse<Boolean> deleteConversation(String conversationId)
    {
        long userId = UserContextService.getCurrentUserId();

        int updated = conversationMapper.deleteConversation(conversationId, userId);
        if (updated <= 0)
            return ServiceResponse.buildErrorResponse(ERROR_CONVERSATION_NOT_FOUND, "Conversation does not exist.");

        return ServiceResponse.buildSuccessResponse(true);
    }

    @Transactional(rollbackFor = Exception.class)
    public ServiceResponse<Boolean> deleteMessages(DeleteMessagesRequest request)
    {
        long userId = UserContextService.getCurrentUserId();

        Conversation conversation = conversationMapper.getConversationByIdAndUser(request.getConversationId(), userId);
        if (conversation == null)
            return ServiceResponse.buildErrorResponse(ERROR_CONVERSATION_NOT_FOUND, "Conversation does not exist.");

        conversationMessageMapper.deleteMessages(request.getConversationId(), userId, request.getMessageIds());
        return ServiceResponse.buildSuccessResponse(true);
    }

    @Transactional(rollbackFor = Exception.class)
    public ServiceResponse<Boolean> pinConversation(@Valid PinConversationRequest request)
    {
        long userId = UserContextService.getCurrentUserId();

        if (request == null || CollectionUtils.isEmpty(request.getConversationPinOrders()))
        {
            conversationGroupRelationMapper.deletePinnedConversationRelations(userId);
            return ServiceResponse.buildSuccessResponse(true);
        }

        Map<String, Integer> orderedConversations = new LinkedHashMap<>();
        for (ConversationPinOrder conversationPinOrder : request.getConversationPinOrders())
        {
            if (conversationPinOrder == null)
                continue;

            String conversationId = TextNormalizer.trimToNull(conversationPinOrder.getConversationId());
            if (conversationId != null)
                orderedConversations.put(conversationId, conversationPinOrder.getSortOrder());
        }

        if (orderedConversations.isEmpty())
        {
            conversationGroupRelationMapper.deletePinnedConversationRelations(userId);
            return ServiceResponse.buildSuccessResponse(true);
        }

        if (!conversationMapper.allOwnedConversationsExist(userId, orderedConversations.keySet()))
            return ServiceResponse.buildErrorResponse(ERROR_INVALID_CONVERSATION, "Some conversations do not exist.");

        conversationGroupRelationMapper.deletePinnedConversationRelations(userId);
        for (Map.Entry<String, Integer> entry : orderedConversations.entrySet())
        {
            ConversationGroupRelation relation = new ConversationGroupRelation();
            relation.setCreatorId(userId);
            relation.setModifierId(userId);
            relation.setConversationId(entry.getKey());
            relation.setConversationGroupId(null);
            relation.setSortOrder(entry.getValue());
            conversationGroupRelationMapper.insertPinnedConversationRelation(relation);
        }

        return ServiceResponse.buildSuccessResponse(true);
    }

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

    private ConversationMessageInfo toConversationMessageInfo(ConversationMessage message)
    {
        ConversationMessageInfo messageInfo = new ConversationMessageInfo();
        messageInfo.setId(message.getId());
        messageInfo.setRole(message.getRole());
        messageInfo.setName(message.getName());
        messageInfo.setContent(message.getContent());
        messageInfo.setContentParts(readJsonList(message.getContentParts(), CONTENT_PARTS_TYPE));
        messageInfo.setToolCalls(readJsonList(message.getToolCalls(), TOOL_CALLS_TYPE));
        messageInfo.setToolCallId(message.getToolCallId());
        messageInfo.setAgentId(message.getAgentId());
        messageInfo.setFinishReason(message.getFinishReason());
        return messageInfo;
    }

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

    private ConversationAbstract toConversationAbstract(Conversation conversation)
    {
        ConversationAbstract conversationAbstract = new ConversationAbstract();
        conversationAbstract.setConversationId(conversation.getConversationId());
        conversationAbstract.setTitle(conversation.getTitle());
        conversationAbstract.setCreationTime(conversation.getCreationTime());
        conversationAbstract.setModificationTime(conversation.getModificationTime());
        return conversationAbstract;
    }

    private ConversationMessage copyMessageForConversation(ConversationMessage source, String conversationId, long userId)
    {
        ConversationMessage target = new ConversationMessage();
        target.setCreatorId(userId);
        target.setModifierId(userId);
        target.setConversationId(conversationId);
        target.setRole(source.getRole());
        target.setName(source.getName());
        target.setContent(source.getContent());
        target.setContentParts(source.getContentParts());
        target.setToolCalls(source.getToolCalls());
        target.setToolCallId(source.getToolCallId());
        target.setAgentId(source.getAgentId());
        target.setFinishReason(source.getFinishReason());
        target.setDeleted(false);
        return target;
    }

    private ExportPayload buildExportPayload(ConversationMessageHistory history, ExportFormat exportFormat) throws IOException
    {
        String baseFilename = history.getConversationId() + "." + exportFormat.name().toLowerCase(Locale.ROOT);
        return switch (exportFormat)
        {
            case JSON -> new ExportPayload(baseFilename, MediaType.APPLICATION_JSON_VALUE, objectMapper.writeValueAsString(history));
            case HTML -> new ExportPayload(baseFilename, MediaType.TEXT_HTML_VALUE, toHtml(history));
            case MARKDOWN -> new ExportPayload(baseFilename, "text/markdown;charset=UTF-8", toMarkdown(history));
            case TXT -> new ExportPayload(baseFilename, MediaType.TEXT_PLAIN_VALUE, toPlainText(history));
            case PDF, DOCX -> new ExportPayload(baseFilename, MediaType.TEXT_PLAIN_VALUE, toPlainText(history));
        };
    }

    private String toPlainText(ConversationMessageHistory history)
    {
        StringBuilder builder = new StringBuilder();
        builder.append(history.getTitle()).append(System.lineSeparator()).append(System.lineSeparator());
        for (ConversationMessageInfo message : history.getMessages())
            builder.append(message.getRole()).append(": ").append(TextNormalizer.trimToEmpty(message.getContent())).append(System.lineSeparator());

        return builder.toString();
    }

    private String toMarkdown(ConversationMessageHistory history)
    {
        StringBuilder builder = new StringBuilder();
        builder.append("# ").append(history.getTitle()).append(System.lineSeparator()).append(System.lineSeparator());
        for (ConversationMessageInfo message : history.getMessages())
            builder.append("## ").append(message.getRole()).append(System.lineSeparator()).append(TextNormalizer.trimToEmpty(message.getContent())).append(System.lineSeparator()).append(System.lineSeparator());

        return builder.toString();
    }

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

    private static int normalizePageIndex(int pageIndex)
    {
        return pageIndex <= 0 ? DEFAULT_PAGE_INDEX : pageIndex;
    }

    private static int normalizePageSize(int pageSize)
    {
        if (pageSize <= 0)
            return DEFAULT_PAGE_SIZE;

        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    private record ExportPayload(String filename, String contentType, String content)
    {
    }
}
