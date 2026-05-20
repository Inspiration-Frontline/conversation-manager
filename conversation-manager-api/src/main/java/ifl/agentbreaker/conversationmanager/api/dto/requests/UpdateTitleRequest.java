package ifl.agentbreaker.conversationmanager.api.dto.requests;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateTitleRequest
{
    @NotBlank(message = "Conversation ID is required.")
    private String conversationId;

    @NotBlank(message = "Title is required.")
    private String title;
}
