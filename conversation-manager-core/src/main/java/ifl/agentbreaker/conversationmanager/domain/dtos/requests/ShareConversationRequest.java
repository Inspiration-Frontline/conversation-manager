package ifl.agentbreaker.conversationmanager.domain.dtos.requests;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ShareConversationRequest
{
    @NotBlank(message = "Conversation ID is required.")
    private String conversationId;

    /** ONE_DAY, SEVEN_DAYS (default), THIRTY_DAYS, or NEVER. */
    private String expiry = "SEVEN_DAYS";
}
