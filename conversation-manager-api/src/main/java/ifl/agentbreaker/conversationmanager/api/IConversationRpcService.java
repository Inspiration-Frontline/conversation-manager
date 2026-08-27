package ifl.agentbreaker.conversationmanager.api;

import ifl.agentbreaker.conversationmanager.api.dto.requests.UpdateTitleRequest;
import ifl.agentbreaker.conversationmanager.api.dto.responses.ConversationAbstract;
import jakarta.validation.Valid;
import stark.dataworks.boot.web.ServiceResponse;

public interface IConversationRpcService
{
    ServiceResponse<ConversationAbstract> updateTitle(@Valid UpdateTitleRequest request);
}
