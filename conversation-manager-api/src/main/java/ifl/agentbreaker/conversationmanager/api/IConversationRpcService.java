package ifl.agentbreaker.conversationmanager.api;

import ifl.agentbreaker.conversationmanager.api.dto.requests.DeleteMessagesRequest;
import ifl.agentbreaker.conversationmanager.api.dto.requests.UpdateTitleRequest;
import ifl.agentbreaker.conversationmanager.api.dto.responses.ConversationAbstract;
import ifl.agentbreaker.conversationmanager.api.dto.responses.ConversationMessageHistory;
import jakarta.validation.Valid;
import stark.dataworks.boot.web.ServiceResponse;

public interface IConversationRpcService
{
    ServiceResponse<ConversationAbstract> updateTitle(@Valid UpdateTitleRequest request);
    ServiceResponse<Boolean> deleteMessages(DeleteMessagesRequest request);
    ServiceResponse<ConversationMessageHistory> getConversationMessageHistory(String conversationId);
}
