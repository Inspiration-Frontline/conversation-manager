package ifl.agentbreaker.conversationmanager.domain.dtos.responses;

import lombok.Data;

import java.util.Date;

@Data
public class FileDownloadUrl
{
    private String fileId;
    private String url;
    private Date expiresAt;
}
