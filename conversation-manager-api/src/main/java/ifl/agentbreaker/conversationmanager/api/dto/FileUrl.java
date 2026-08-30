package ifl.agentbreaker.conversationmanager.api.dto;

import lombok.Data;

/** Provider-bound file location carried by a structured content part. */
@Data
public class FileUrl
{
    /** Authorized URL passed to the model provider. */
    private String url;
    /** Provider image-detail hint, when applicable. */
    private String detail;
}
