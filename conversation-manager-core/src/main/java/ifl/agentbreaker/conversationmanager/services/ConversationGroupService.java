package ifl.agentbreaker.conversationmanager.services;

import ifl.agentbreaker.conversationmanager.domain.dtos.requests.*;
import ifl.agentbreaker.conversationmanager.domain.dtos.responses.ConversationGroupAbstract;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import stark.dataworks.boot.autoconfig.web.LogArgumentsAndResponse;
import stark.dataworks.boot.web.ServiceResponse;

import java.util.List;

@Slf4j
@Service
@Validated
@LogArgumentsAndResponse
public class ConversationGroupService
{
    /**
     * Create a new conversation group, by default, the sort is (current max sort order + 1).
     * @param request
     * @return
     */
    public ServiceResponse<ConversationGroupAbstract> createConversationGroup(@Valid CreateConversationGroupRequest request)
    {
        return null;
    }

    public ServiceResponse<ConversationGroupAbstract> updateConversationGroupAbstract(@Valid UpdateConversationGroupAbstractRequest request)
    {
        return null;
    }

    public ServiceResponse<List<ConversationGroupAbstract>> reorderConversationGroup(@Valid ReorderConversationGroupRequest request)
    {
        return null;
    }

    public ServiceResponse<Boolean> deleteConversationGroup(@Valid DeleteConversationGroupRequest request)
    {
        return null;
    }

    public ServiceResponse<List<ConversationGroupAbstract>> getConversationGroupsOfUser()
    {
        return null;
    }

    public ServiceResponse<Boolean> addConversationsToGroup(@Valid AddConversationToGroupRequest request)
    {
        // TODO:
        // 1. Validate if the conversation exists & belongs to the current user.
        // 2. Validate if the conversation group exists & belongs to the current user.
        return null;
    }

    public ServiceResponse<Boolean> removeConversationsFromGroup(@Valid RemoveConversationFromGroupRequest request)
    {
        // TODO:
        // 1. Validate if the conversation exists & belongs to the current user.
        // 2. Validate if the conversation group exists & belongs to the current user.
        return null;
    }
}
