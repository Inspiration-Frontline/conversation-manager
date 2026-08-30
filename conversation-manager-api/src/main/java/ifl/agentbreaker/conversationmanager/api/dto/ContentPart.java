package ifl.agentbreaker.conversationmanager.api.dto;

import lombok.Data;

/** One structured text or file content part in the legacy Java RPC DTO contract. */
@Data
public class ContentPart
{
    /** Provider-neutral content-part discriminator. */
    private String type;
    /** Text payload, populated only for text parts. */
    private String text;
    /** File location and rendering detail, populated only for file parts. */
    private FileUrl fileUrl;
}
