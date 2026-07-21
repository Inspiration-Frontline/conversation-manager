package ifl.agentbreaker.conversationmanager.domain.dtos.responses;

import lombok.Data;

import java.time.Instant;

@Data
public class FileDownloadUrl
{
    private String fileId;
    private String url;
    private Instant expiresAt;
}
