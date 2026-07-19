package ifl.agentbreaker.conversationmanager.domain.dtos.requests;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import java.util.List;

@Data
public class ConfirmFileUploadRequest
{
    @NotEmpty(message = "At least one file is required.")
    @Valid
    private List<ConfirmFileUploadItem> files;
}
