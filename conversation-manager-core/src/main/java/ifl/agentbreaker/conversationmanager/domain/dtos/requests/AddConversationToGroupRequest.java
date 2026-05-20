package ifl.agentbreaker.conversationmanager.domain.dtos.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class AddConversationToGroupRequest
{
    @NotBlank(message = "Conversation group ID is required.")
    private String conversationGroupId;

    @NotEmpty(message = "Conversation IDs are required.")
    private List<String> conversationIds;
}
