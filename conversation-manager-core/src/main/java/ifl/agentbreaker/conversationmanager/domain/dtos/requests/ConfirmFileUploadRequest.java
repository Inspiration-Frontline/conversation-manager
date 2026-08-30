package ifl.agentbreaker.conversationmanager.domain.dtos.requests;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import java.util.List;

/** Batch of direct-to-OSS uploads that are ready for server-side confirmation. */
@Data
public class ConfirmFileUploadRequest
{
    /** Uploaded resources and checksums to verify atomically at the HTTP boundary. */
    @NotEmpty(message = "At least one file is required.")
    @Valid
    private List<ConfirmFileUploadItem> files;
}
