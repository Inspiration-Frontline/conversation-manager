package ifl.agentbreaker.conversationmanager.domain.dtos.responses;

import lombok.Data;

import java.time.Instant;

@Data
public class FileUploadSession
{
    private FileResourceInfo file;
    private String method;
    private String uploadUrl;
    private Instant expiresAt;
}
