package ifl.agentbreaker.conversationmanager.domain.dtos.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/** One uploaded object and its client-computed checksum in a batch confirmation request. */
@Data
public class ConfirmFileUploadItem
{
    @NotBlank(message = "File ID is required.")
    private String fileId;

    @Pattern(regexp = "^[0-9a-fA-F]{64}$")
    private String sha256;
}
