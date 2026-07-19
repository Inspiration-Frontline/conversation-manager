package ifl.agentbreaker.conversationmanager.api.dto.requests;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import java.util.List;

@Data
public class DeleteConversationRequest
{
    @NotEmpty(message = "At least one conversation is required.")
    private List<String> conversationIds;
}
