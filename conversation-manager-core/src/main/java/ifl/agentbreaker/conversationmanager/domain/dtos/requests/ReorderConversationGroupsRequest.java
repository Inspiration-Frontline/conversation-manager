package ifl.agentbreaker.conversationmanager.domain.dtos.requests;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.util.List;

@Data
public class ReorderConversationGroupsRequest
{
    /**
     * Complete ordered set of Group IDs owned by the current user.
     */
    @NotEmpty(message = "Conversation group IDs are required.")
    private List<@NotNull @Positive Long> conversationGroupIds;
}
