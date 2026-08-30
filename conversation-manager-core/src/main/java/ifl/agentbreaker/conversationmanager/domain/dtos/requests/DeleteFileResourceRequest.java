package ifl.agentbreaker.conversationmanager.domain.dtos.requests;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import java.util.List;

/** Request to logically delete one or more file resources owned by the caller. */
@Data
public class DeleteFileResourceRequest
{
    /** Stable identifiers of the files selected for deletion. */
    @NotEmpty(message = "At least one file is required.")
    private List<String> fileIds;
}
