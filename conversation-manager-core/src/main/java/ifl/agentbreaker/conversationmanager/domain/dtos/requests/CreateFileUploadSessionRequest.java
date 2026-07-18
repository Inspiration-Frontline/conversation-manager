package ifl.agentbreaker.conversationmanager.domain.dtos.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateFileUploadSessionRequest
{
    @NotBlank
    @Size(max = 255)
    private String originalFilename;

    @NotBlank
    @Size(max = 128)
    private String mimeType;

    @Positive
    private long fileSize;
}
