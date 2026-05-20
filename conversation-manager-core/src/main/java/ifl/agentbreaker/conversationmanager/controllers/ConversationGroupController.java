package ifl.agentbreaker.conversationmanager.controllers;

import ifl.agentbreaker.conversationmanager.domain.dtos.requests.*;
import ifl.agentbreaker.conversationmanager.domain.dtos.responses.ConversationGroupAbstract;
import ifl.agentbreaker.conversationmanager.services.ConversationGroupService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import stark.dataworks.boot.web.ServiceResponse;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/conversation-group")
public class ConversationGroupController
{
    @Autowired
    private ConversationGroupService conversationGroupService;

    @PostMapping("/new")
    public ServiceResponse<ConversationGroupAbstract> createConversationGroup(@Valid @RequestBody CreateConversationGroupRequest request)
    {
        return conversationGroupService.createConversationGroup(request);
    }

    @PutMapping("/abstract")
    public ServiceResponse<ConversationGroupAbstract> updateConversationGroupAbstract(@Valid @RequestBody UpdateConversationGroupAbstractRequest request)
    {
        return conversationGroupService.updateConversationGroupAbstract(request);
    }

    @PutMapping("/reorder")
    public ServiceResponse<List<ConversationGroupAbstract>> reorderConversationGroups(@Valid @RequestBody ReorderConversationGroupRequest request)
    {
        return conversationGroupService.reorderConversationGroup(request);
    }

    @DeleteMapping
    public ServiceResponse<Boolean> deleteConversationGroup(@Valid @RequestBody DeleteConversationGroupRequest request)
    {
        return conversationGroupService.deleteConversationGroup(request);
    }

    @GetMapping("/list")
    public ServiceResponse<List<ConversationGroupAbstract>> getConversationGroups()
    {
        return conversationGroupService.getConversationGroupsOfUser();
    }

    @PostMapping("/conversations/add")
    public ServiceResponse<Boolean> addConversationsToGroup(@Valid @RequestBody AddConversationToGroupRequest request)
    {
        return conversationGroupService.addConversationsToGroup(request);
    }

    @PostMapping("/conversations/remove")
    public ServiceResponse<Boolean> removeConversationsFromGroup(@Valid @RequestBody RemoveConversationFromGroupRequest request)
    {
        return conversationGroupService.removeConversationsFromGroup(request);
    }
}
