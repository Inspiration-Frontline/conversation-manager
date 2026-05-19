package ifl.agentbreaker.conversationmanager.controllers;

import ifl.agentbreaker.conversationmanager.api.dto.responses.ConversationAbstract;
import ifl.agentbreaker.conversationmanager.services.ConversationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import stark.dataworks.boot.web.ServiceResponse;

@Slf4j
@RestController
@RequestMapping("/conversation")
public class ConversationController
{
    @Autowired
    private ConversationService conversationService;

    @PostMapping("/new")
    public ServiceResponse<ConversationAbstract> createConversation()
    {
        return conversationService.createConversation();
    }
}
