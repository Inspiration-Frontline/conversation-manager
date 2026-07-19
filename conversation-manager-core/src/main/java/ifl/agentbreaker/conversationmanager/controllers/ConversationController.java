package ifl.agentbreaker.conversationmanager.controllers;

import ifl.agentbreaker.authcenter.session.UserContextService;
import ifl.agentbreaker.conversationmanager.api.dto.responses.ConversationAbstract;
import ifl.agentbreaker.conversationmanager.api.dto.responses.ConversationMessageHistory;
import ifl.agentbreaker.conversationmanager.api.dto.requests.DeleteMessagesRequest;
import ifl.agentbreaker.conversationmanager.api.dto.requests.UpdateTitleRequest;
import ifl.agentbreaker.conversationmanager.domain.dtos.requests.*;
import ifl.agentbreaker.conversationmanager.domain.dtos.responses.ConversationSharingResult;
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

@Slf4j
@RestController
@RequestMapping("/conversation")
public class ConversationController
{
    @Autowired
    private ConversationService conversationService;

    @Autowired
    private ConversationRoundService conversationRoundService;

    @PostMapping("/new")
    public ServiceResponse<ConversationAbstract> createConversation()
    {
        return conversationService.createConversation();
    }

    @GetMapping("/{conversationId}")
    public ServiceResponse<ConversationAbstract> getConversation(@PathVariable String conversationId)
    {
        return conversationService.getConversationInfo(conversationId);
    }

    @GetMapping("/list")
    public ServiceResponse<PaginatedData<ConversationAbstract>> getConversations(@Valid GetConversationsRequest request)
    {
        return conversationService.getConversations(request);
    }

    @GetMapping("/{conversationId}/messages")
    public ServiceResponse<ConversationMessageHistory> getConversationMessages(@PathVariable String conversationId)
    {
        return conversationService.getConversationMessageHistory(conversationId);
    }

    @GetMapping("/{conversationId}/rounds")
    public ServiceResponse<RoundHistoryView> getConversationRounds(@PathVariable String conversationId)
    {
        return conversationRoundService.getHttpHistory(UserContextService.getCurrentUserId(), conversationId);
    }

    @PutMapping("/title")
    public ServiceResponse<ConversationAbstract> updateTitle(@Valid @RequestBody UpdateTitleRequest request)
    {
        return conversationService.updateTitle(request);
    }

    @DeleteMapping("/{conversationId}")
    public ServiceResponse<Boolean> deleteConversation(@PathVariable String conversationId)
    {
        return conversationService.deleteConversation(conversationId);
    }

    @DeleteMapping("/messages")
    public ServiceResponse<Boolean> deleteMessages(@Valid @RequestBody DeleteMessagesRequest request)
    {
        return conversationService.deleteMessages(request);
    }

    @PostMapping("/share")
    public ServiceResponse<ConversationSharingResult> shareConversation(@Valid @RequestBody ShareConversationRequest request)
    {
        return conversationService.shareConversation(request);
    }

    @PostMapping("/fork")
    public ServiceResponse<ConversationAbstract> forkConversation(@Valid @RequestBody ForkConversationRequest request)
    {
        return conversationService.forkConversation(request);
    }

    @GetMapping("/export/formats")
    public ServiceResponse<List<String>> getExportFormats()
    {
        return conversationService.getAcceptableExportFormats();
    }

    @PostMapping("/export")
    public void exportConversation(@Valid @RequestBody ExportConversationRequest request, HttpServletResponse response)
    {
        conversationService.exportConversation(request, response);
    }

    @PutMapping("/pin")
    public ServiceResponse<Boolean> pinConversations(@Valid @RequestBody PinConversationRequest request)
    {
        return conversationService.pinConversation(request);
    }
}
