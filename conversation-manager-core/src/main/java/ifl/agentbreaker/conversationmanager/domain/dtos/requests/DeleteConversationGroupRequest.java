package ifl.agentbreaker.conversationmanager.domain.dtos.requests;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DeleteConversationGroupRequest
{
    @NotBlank(message = "Conversation group ID is required.")
    private String groupId;

    private boolean deleteConversations;
}
