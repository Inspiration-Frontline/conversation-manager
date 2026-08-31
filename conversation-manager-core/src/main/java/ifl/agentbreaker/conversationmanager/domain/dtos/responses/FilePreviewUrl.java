package ifl.agentbreaker.conversationmanager.domain.dtos.responses;

import lombok.Data;

import java.time.Instant;

/** Short-lived inline URL and stable dimensions for one sanitized image preview. */
@Data
public class FilePreviewUrl
{
    /** Stable original file identity. */
    private String fileId;
    /** Short-lived URL for the sanitized derivative. */
    private String url;
    /** UTC expiry of the signed URL. */
    private Instant expiresAt;
    /** MIME type of the sanitized derivative. */
    private String mimeType;
    /** Verified derivative width in pixels. */
    private int width;
    /** Verified derivative height in pixels. */
    private int height;
}
