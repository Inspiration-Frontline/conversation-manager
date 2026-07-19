package ifl.agentbreaker.conversationmanager.domain.dtos.requests;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RetryFileProcessingRequest
{
    @NotBlank(message = "File ID is required.")
    private String fileId;
}
