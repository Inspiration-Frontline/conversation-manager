package ifl.agentbreaker.conversationmanager.api;

import ifl.agentbreaker.conversationmanager.api.dto.requests.UpdateTitleRequest;
import ifl.agentbreaker.conversationmanager.api.dto.responses.ConversationAbstract;
import jakarta.validation.Valid;
import stark.dataworks.boot.web.ServiceResponse;

/** Legacy Java RPC contract for updating owner-visible Conversation metadata. */
public interface IConversationRpcService
{
    /** Updates the title of an owned Conversation.
     * @param request Conversation identity and normalized replacement title
     * @return updated Conversation summary or a client-safe service error
     */
    ServiceResponse<ConversationAbstract> updateTitle(@Valid UpdateTitleRequest request);
}
