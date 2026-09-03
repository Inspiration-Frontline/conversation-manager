package ifl.agentbreaker.conversationmanager.controllers;

import ifl.agentbreaker.authcenter.session.UserContextService;
import ifl.agentbreaker.conversationmanager.api.dto.responses.ConversationAbstract;
import ifl.agentbreaker.conversationmanager.api.dto.requests.DeleteConversationRequest;
import ifl.agentbreaker.conversationmanager.api.dto.requests.UpdateTitleRequest;
import ifl.agentbreaker.conversationmanager.domain.dtos.requests.*;
import ifl.agentbreaker.conversationmanager.domain.dtos.responses.ConversationSharingResult;
import ifl.agentbreaker.conversationmanager.domain.dtos.responses.ConversationShareSummary;
import ifl.agentbreaker.conversationmanager.domain.dtos.responses.ResolvedConversationReference;
import ifl.agentbreaker.conversationmanager.domain.dtos.responses.SharedConversationView;
import ifl.agentbreaker.conversationmanager.domain.dtos.responses.RoundHistoryView;
import ifl.agentbreaker.conversationmanager.services.ConversationService;
import ifl.agentbreaker.conversationmanager.services.rounds.ConversationRoundService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import stark.dataworks.boot.web.PaginatedData;
import stark.dataworks.boot.web.ServiceResponse;

import java.util.List;

/** HTTP boundary for owner-scoped Conversation metadata, history, sharing, and export operations. */
@Slf4j
@RestController
@RequestMapping("/conversation")
public class ConversationController
{
    /** Service owning Conversation metadata, sharing, and export behavior. */
    @Autowired
    private ConversationService conversationService;

    /** Service owning durable Round history and reference resolution. */
    @Autowired
    private ConversationRoundService conversationRoundService;

    /**
     * Creates the durable Conversation shell used before the first Agent Runner request.
     *
     * <p>The browser needs an ID before it can stream a Round, and keeping this shell in the
     * Conversation Manager makes a refresh or a failed first generation addressable.</p>
     *
     * @param request optional group placement for the new Conversation
     * @return the newly created owned Conversation summary
     */
    @PostMapping("/new")
    public ServiceResponse<ConversationAbstract> createConversation(
        @Valid @RequestBody(required = false) CreateConversationRequest request)
    {
        return conversationService.createConversation(request);
    }

    /**
     * Loads one Conversation after enforcing ownership through the current user context.
     *
     * @param conversationId stable Conversation identifier from the route
     * @return the owned summary, or the service error for a missing/non-owned Conversation
     */
    @GetMapping("/{conversationId}")
    public ServiceResponse<ConversationAbstract> getConversation(@PathVariable String conversationId)
    {
        return conversationService.getConversationInfo(conversationId);
    }

    /**
     * Returns the paginated root list consumed by the sidebar.
     *
     * @param request page, size, and optional root-list filters validated from query parameters
     * @return one page of Conversations owned by the authenticated user
     */
    @GetMapping("/list")
    public ServiceResponse<PaginatedData<ConversationAbstract>> getConversations(@Valid GetConversationsRequest request)
    {
        return conversationService.getConversations(request);
    }

    /**
     * Returns persisted Round history, including attachment summaries needed to rebuild the UI.
     *
     * @param conversationId stable Conversation identifier from the route
     * @return ordered Round summaries and the latest persisted Round number
     */
    @GetMapping("/{conversationId}/rounds")
    public ServiceResponse<RoundHistoryView> getConversationRounds(@PathVariable String conversationId)
    {
        return conversationRoundService.getHttpHistory(UserContextService.getCurrentUserId(), conversationId);
    }

    /**
     * Freezes ordered source titles and high-water boundaries without loading message bodies.
     *
     * @param request destination scope and ordered source Conversation identifiers
     * @return ordered title and boundary snapshots, or a client-safe validation error
     */
    @PostMapping("/references/resolve")
    public ServiceResponse<List<ResolvedConversationReference>> resolveConversationReferences(
        @Valid @RequestBody ResolveConversationReferencesRequest request)
    {
        return conversationRoundService.resolveConversationReferences(request);
    }

    /**
     * Changes the display title without changing Round or message data.
     *
     * @param request owned Conversation ID and normalized replacement title
     * @return the updated Conversation summary
     */
    @PutMapping("/title")
    public ServiceResponse<ConversationAbstract> updateTitle(@Valid @RequestBody UpdateTitleRequest request)
    {
        return conversationService.updateTitle(request);
    }

    /**
     * Soft-deletes owned Conversations and releases their file references in one service call.
     *
     * @param request one or more Conversation IDs supplied in the request body
     * @return {@code true} after the logical delete and reference release are committed
     */
    @DeleteMapping
    public ServiceResponse<Boolean> deleteConversation(@Valid @RequestBody DeleteConversationRequest request)
    {
        return conversationService.deleteConversations(request.getConversationIds());
    }

    /**
     * Creates a share snapshot/record for an owned Conversation.
     *
     * @param request Conversation ID and share options
     * @return public share information that can be opened without exposing private ownership data
     */
    @PostMapping("/share")
    public ServiceResponse<ConversationSharingResult> shareConversation(@Valid @RequestBody ShareConversationRequest request)
    {
        return conversationService.shareConversation(request);
    }

    /** Returns owner-scoped share records, optionally filtered to one Conversation.
     * @param conversationId optional owned Conversation identity
     * @return ordered share metadata visible to the current owner
     */
    @GetMapping("/shares")
    public ServiceResponse<List<ConversationShareSummary>> listConversationShares(
        @RequestParam(required = false) String conversationId)
    {
        return conversationService.listConversationShares(conversationId);
    }

    /** Revokes one owner-created share link.
     * @param sharedConversationId stable share identity to revoke
     * @return {@code true} after the revocation timestamp is committed
     */
    @PostMapping("/share/revoke")
    public ServiceResponse<Boolean> revokeConversationShare(@RequestParam String sharedConversationId)
    {
        return conversationService.revokeConversationShare(sharedConversationId);
    }

    /** Reads an authenticated immutable Conversation snapshot.
     * @param sharedConversationId stable share identity from the public route
     * @return redacted history bounded by the share snapshot
     */
    @GetMapping("/shared/{sharedConversationId}")
    public ServiceResponse<SharedConversationView> getSharedConversation(@PathVariable String sharedConversationId)
    {
        return conversationService.getSharedConversation(sharedConversationId);
    }

    /**
     * Copies a shared Conversation into a new Conversation owned by the caller.
     *
     * @param request source share identifier and optional fork metadata
     * @return the new owned Conversation summary
     */
    @PostMapping("/fork")
    public ServiceResponse<ConversationAbstract> forkConversation(@Valid @RequestBody ForkConversationRequest request)
    {
        return conversationService.forkConversation(request);
    }

    /**
     * Lists formats for which this service has a real exporter implementation.
     *
     * @return supported export format identifiers; unsupported formats are intentionally omitted
     */
    @GetMapping("/export/formats")
    public ServiceResponse<List<String>> getExportFormats()
    {
        return conversationService.getAcceptableExportFormats();
    }

    /**
     * Streams a generated export so large histories are not buffered in the browser response.
     *
     * @param request Conversation ID and requested format
     * @param response servlet response receiving content type, headers, and bytes
     */
    @PostMapping("/export")
    public void exportConversation(@Valid @RequestBody ExportConversationRequest request, HttpServletResponse response)
    {
        conversationService.exportConversation(request, response);
    }

    /**
     * Updates the root-level pinned flag for one or more owned Conversations.
     *
     * @param request Conversation IDs and desired pinned state
     * @return {@code true} after all requested rows are updated
     */
    @PutMapping("/pin")
    public ServiceResponse<Boolean> pinConversations(@Valid @RequestBody PinConversationRequest request)
    {
        return conversationService.pinConversation(request);
    }
}
