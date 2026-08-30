package ifl.agentbreaker.conversationmanager.domain.dtos.responses;

import lombok.Data;

import java.time.Instant;

/** Short-lived object-store URL returned for an authorized file download. */
@Data
public class FileDownloadUrl
{
    /** Stable identifier of the file. */
    private String fileId;
    /** Signed URL that grants temporary read access to the file object. */
    private String url;
    /** UTC instant marking expires at. */
    private Instant expiresAt;
}
