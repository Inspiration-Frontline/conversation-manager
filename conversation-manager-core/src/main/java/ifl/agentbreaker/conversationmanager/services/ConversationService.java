package ifl.agentbreaker.conversationmanager.services;

import ifl.agentbreaker.conversationmanager.api.IConversationRpcService;
import ifl.agentbreaker.conversationmanager.api.dto.requests.DeleteMessagesRequest;
import ifl.agentbreaker.conversationmanager.api.dto.requests.UpdateTitleRequest;
import ifl.agentbreaker.conversationmanager.domain.dtos.requests.*;
import ifl.agentbreaker.conversationmanager.api.dto.responses.ConversationAbstract;
import ifl.agentbreaker.conversationmanager.api.dto.responses.ConversationMessageHistory;
import ifl.agentbreaker.conversationmanager.domain.dtos.responses.ConversationSharingResult;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import stark.dataworks.boot.autoconfig.web.LogArgumentsAndResponse;
import stark.dataworks.boot.web.PaginatedData;
import stark.dataworks.boot.web.ServiceResponse;

import java.util.List;

@Slf4j
@Service
@DubboService
@Validated
@LogArgumentsAndResponse
public class ConversationService implements IConversationRpcService
{
    /**
     * Create a new conversation.
     * @return The created conversation.
     */
    public ServiceResponse<ConversationAbstract> createConversation()
    {
        return null;
    }

    public ServiceResponse<ConversationAbstract> getConversationInfo(String conversationId)
    {
        return null;
    }

    public ServiceResponse<ConversationSharingResult> shareConversation(ShareConversationRequest request)
    {
        // Get the max message id of the conversation when calling this method as the end message id.
        return null;
    }

    /**
     * Forks a conversation from a shared conversation.
     * @return The forked conversation.
     */
    public ServiceResponse<ConversationAbstract> forkConversation(@Valid ForkConversationRequest request)
    {
        return null;
    }

    public ServiceResponse<PaginatedData<ConversationAbstract>> getConversations(@Valid GetConversationsRequest request)
    {
        return null;
    }

    public ServiceResponse<ConversationMessageHistory> getConversationMessageHistory(String conversationId)
    {
        return null;
    }

    public ServiceResponse<List<String>> getAcceptableExportFormats()
    {
        // Return all acceptable export formats from ifl.agentbreaker.conversationmanager.domain.constants.ExportFormat.
        return null;
    }

    /**
     * Exports a conversation to a file with the specified format.
     * @param request
     */
    public void exportConversation(@Valid ExportConversationRequest request, HttpServletResponse response)
    {
        // Validate exportFormat.

        // Note that only the user who owns the conversation can export it & download the file.

        // Regenerate the file every time the user requests it.
        // Because for most (maybe >= 80%) of the requests, the user will export the conversation for only once.
        // Save the exported file in OSS would only increase our costs with limited even ignorable benefits.
    }

    public ServiceResponse<ConversationAbstract> updateTitle(@Valid UpdateTitleRequest request)
    {
        return null;
    }

    public ServiceResponse<Boolean> deleteConversation(String conversationId)
    {
        // Q: How to delete related user profile info if a conversation is deleted?

        return null;
    }

    public ServiceResponse<Boolean> deleteMessages(DeleteMessagesRequest request)
    {
        return null;
    }

    public ServiceResponse<Boolean> pinConversation(@Valid PinConversationRequest request)
    {
        return null;
    }
}
