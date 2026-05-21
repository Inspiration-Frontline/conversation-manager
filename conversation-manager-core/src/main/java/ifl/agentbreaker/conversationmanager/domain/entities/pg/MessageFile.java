package ifl.agentbreaker.conversationmanager.domain.entities.pg;

import ifl.agentbreaker.conversationmanager.domain.constants.FileType;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class MessageFile extends EntityBase
{
    /**
     * Type of the file.
     */
    private FileType fileType;

    /**
     * URL of the original file in OSS.
     */
    private String ossUrl;

    /**
     * URL of the thumbnail image in OSS.
     * Only for image and video files.
     */
    private String thumbnailUrl;

    /**
     * Size of the file in bytes.
     */
    private long fileSize;

    /**
     * MIME type of the file.
     */
    private String mimeType;

    /**
     * Original filename when uploaded.
     */
    private String originalFilename;

    /**
     * Width of the image or video in pixels.
     */
    private Integer width;

    /**
     * Height of the image or video in pixels.
     */
    private Integer height;
}
