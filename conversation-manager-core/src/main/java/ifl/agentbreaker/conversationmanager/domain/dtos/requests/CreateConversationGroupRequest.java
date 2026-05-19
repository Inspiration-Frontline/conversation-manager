package ifl.agentbreaker.conversationmanager.domain.dtos.requests;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateConversationGroupRequest
{
    @NotNull(message = "Name is required.")
    private String name;

    private String description;
}
