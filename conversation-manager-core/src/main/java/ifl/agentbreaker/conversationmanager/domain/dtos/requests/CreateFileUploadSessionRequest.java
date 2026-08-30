package ifl.agentbreaker.conversationmanager.domain.dtos.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Request metadata used to reserve a file resource before uploading its bytes. */
@Data
public class CreateFileUploadSessionRequest
{
    /** Client-provided filename retained for display and content-type detection. */
    @NotBlank
    @Size(max = 255)
    private String originalFilename;

    /** Client-declared MIME type used as an initial validation hint. */
    @NotBlank
    @Size(max = 128)
    private String mimeType;

    /** Number of bytes the client intends to upload. */
    @Positive
    private long fileSize;
}
