package ifl.agentbreaker.conversationmanager.domain.dtos.requests;

import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class ConfirmFileUploadRequest
{
    @Pattern(regexp = "^[0-9a-fA-F]{64}$")
    private String sha256;
}
