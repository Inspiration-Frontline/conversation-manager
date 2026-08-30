package ifl.agentbreaker.conversationmanager.controllers;

import ifl.agentbreaker.conversationmanager.domain.dtos.requests.AddConversationToGroupRequest;
import ifl.agentbreaker.conversationmanager.domain.dtos.requests.CreateConversationGroupRequest;
import ifl.agentbreaker.conversationmanager.domain.dtos.requests.DeleteConversationGroupRequest;
import ifl.agentbreaker.conversationmanager.domain.dtos.requests.MoveConversationsRequest;
import ifl.agentbreaker.conversationmanager.domain.dtos.requests.RemoveConversationFromGroupRequest;
import ifl.agentbreaker.conversationmanager.domain.dtos.requests.ReorderConversationGroupsRequest;
import ifl.agentbreaker.conversationmanager.domain.dtos.requests.UpdateConversationGroupAbstractRequest;
import ifl.agentbreaker.conversationmanager.domain.dtos.responses.ConversationGroupAbstract;
import ifl.agentbreaker.conversationmanager.services.ConversationGroupService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import stark.dataworks.boot.web.ServiceResponse;

import java.util.List;

/** HTTP boundary for owner-scoped Group lifecycle, ordering, and Conversation membership. */
@Slf4j
@RestController
@RequestMapping("/conversation-group")
public class ConversationGroupController
{
    /** Service enforcing Group ownership, ordering, and membership transactions. */
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
    public ServiceResponse<List<ConversationGroupAbstract>> reorderConversationGroups(
        @Valid @RequestBody ReorderConversationGroupsRequest request)
    {
        return conversationGroupService.reorderConversationGroups(request);
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
     * Moves owned Conversations directly to one Group or back to the root list.
     *
     * @param request Conversation IDs and nullable target Group ID
     * @return true after the owner-scoped move commits
     */
    @PutMapping("/conversations/move")
    public ServiceResponse<Boolean> moveConversations(@Valid @RequestBody MoveConversationsRequest request)
    {
        return conversationGroupService.moveConversations(request);
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
