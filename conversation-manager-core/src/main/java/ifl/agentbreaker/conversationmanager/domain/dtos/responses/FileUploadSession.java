package ifl.agentbreaker.conversationmanager.domain.dtos.responses;

import lombok.Data;

import java.time.Instant;

@Data
public class FileUploadSession
{
    /** Reserved file metadata and public identifier. */
    private FileResourceInfo file;
    /** HTTP method the client must use for the signed upload URL. */
    private String method;
    /** URL used for upload. */
    private String uploadUrl;
    /** UTC instant marking expires at. */
    private Instant expiresAt;
}
