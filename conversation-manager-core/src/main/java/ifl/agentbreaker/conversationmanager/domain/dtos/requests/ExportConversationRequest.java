package ifl.agentbreaker.conversationmanager.domain.dtos.requests;

import ifl.agentbreaker.conversationmanager.domain.constants.ExportFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ExportConversationRequest
{
    @NotBlank(message = "Conversation ID is required.")
    private String conversationId;

    @NotNull(message = "Export format is required.")
    private ExportFormat exportFormat;
}
