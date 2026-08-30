package ifl.agentbreaker.conversationmanager.domain.dtos.requests;

import ifl.agentbreaker.conversationmanager.domain.constants.ExportFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** Request to render one owned Conversation in a supported export format. */
@Data
public class ExportConversationRequest
{
    /** Stable public identifier of the Conversation. */
    @NotBlank(message = "Conversation ID is required.")
    private String conversationId;

    /** Output representation requested by the caller. */
    @NotNull(message = "Export format is required.")
    private ExportFormat exportFormat;
}
