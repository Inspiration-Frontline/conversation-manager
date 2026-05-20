package ifl.agentbreaker.conversationmanager.api.dto.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class DeleteMessagesRequest
{
    @NotBlank(message = "Conversation ID is required.")
    private String conversationId;

    @NotEmpty(message = "Message IDs are required.")
    private List<Long> messageIds;
}
