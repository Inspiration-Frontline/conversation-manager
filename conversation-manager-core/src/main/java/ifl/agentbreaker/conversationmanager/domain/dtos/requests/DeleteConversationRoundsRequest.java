package ifl.agentbreaker.conversationmanager.domain.dtos.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.util.List;

/** HTTP request for logically deleting one active tail suffix. */
@Data
public class DeleteConversationRoundsRequest
{
    /** Stable owned Conversation identifier. */
    @NotBlank(message = "Conversation ID is required.")
    private String conversationId;

    /** Positive, unique, contiguous active tail Round numbers. */
    @NotEmpty(message = "At least one Round is required.")
    private List<@Positive(message = "Round numbers must be positive.") Long> roundNumbers;
}
