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

    /**
     * Creates a Group owned by the authenticated user.
     *
     * @param request group name and optional description
     * @return persisted Group summary with its stable ordering position
     */
    @PostMapping("/new")
    public ServiceResponse<ConversationGroupAbstract> createConversationGroup(@Valid @RequestBody CreateConversationGroupRequest request)
    {
        return conversationGroupService.createConversationGroup(request);
    }

    /**
     * Updates the display metadata of one owned Group.
     *
     * @param request Group ID and replacement name/description
     * @return updated Group summary
     */
    @PutMapping("/abstract")
    public ServiceResponse<ConversationGroupAbstract> updateConversationGroupAbstract(@Valid @RequestBody UpdateConversationGroupAbstractRequest request)
    {
        return conversationGroupService.updateConversationGroupAbstract(request);
    }

    /**
     * Applies the requested Group order within one transaction.
     *
     * @param request ordered Group IDs
     * @return all owned Groups in their resulting order
     */
    @PutMapping("/reorder")
    public ServiceResponse<List<ConversationGroupAbstract>> reorderConversationGroups(@Valid @RequestBody ReorderConversationGroupRequest request)
    {
        return conversationGroupService.reorderConversationGroup(request);
    }

    /**
     * Deletes an owned Group and applies the request's Conversation retention semantics.
     *
     * @param request Group ID and whether grouped Conversations should also be deleted
     * @return {@code true} after relation and Group rows are removed
     */
    @DeleteMapping
    public ServiceResponse<Boolean> deleteConversationGroup(@Valid @RequestBody DeleteConversationGroupRequest request)
    {
        return conversationGroupService.deleteConversationGroup(request);
    }

    /**
     * Lists Groups in persisted sort order for the authenticated user.
     *
     * @return owned Group summaries
     */
    @GetMapping("/list")
    public ServiceResponse<List<ConversationGroupAbstract>> getConversationGroups()
    {
        return conversationGroupService.getConversationGroupsOfUser();
    }

    /**
     * Adds owned Conversations to an owned Group and clears root pin state.
     *
     * @param request Group ID and Conversation IDs to attach
     * @return {@code true} after relation rows are upserted
     */
    @PostMapping("/conversations/add")
    public ServiceResponse<Boolean> addConversationsToGroup(@Valid @RequestBody AddConversationToGroupRequest request)
    {
        return conversationGroupService.addConversationsToGroup(request);
    }

    /**
     * Removes selected Conversation relations from an owned Group.
     *
     * @param request Group ID and Conversation IDs to detach
     * @return {@code true} after relation rows are deleted
     */
    @PostMapping("/conversations/remove")
    public ServiceResponse<Boolean> removeConversationsFromGroup(@Valid @RequestBody RemoveConversationFromGroupRequest request)
    {
        return conversationGroupService.removeConversationsFromGroup(request);
    }
}
