package ifl.agentbreaker.conversationmanager.domain.dtos.requests;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import java.util.List;

@Data
public class RetryFileProcessingRequest
{
    /** Stable identifiers of the selected file values. */
    @NotEmpty(message = "At least one file is required.")
    private List<String> fileIds;
}
