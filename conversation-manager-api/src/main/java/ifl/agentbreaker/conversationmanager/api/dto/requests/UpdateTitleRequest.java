package ifl.agentbreaker.conversationmanager.api.dto.requests;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** Request that replaces the title of one owned Conversation. */
@Data
public class UpdateTitleRequest
{
    /** Stable public identifier of the Conversation. */
    @NotBlank(message = "Conversation ID is required.")
    private String conversationId;

    /** Non-blank replacement title before service-side normalization. */
    @NotBlank(message = "Title is required.")
    private String title;
}
