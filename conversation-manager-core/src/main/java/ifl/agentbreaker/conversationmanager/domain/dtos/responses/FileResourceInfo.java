package ifl.agentbreaker.conversationmanager.domain.dtos.responses;

import ifl.agentbreaker.conversationmanager.domain.constants.ConversationFileKind;
import ifl.agentbreaker.conversationmanager.domain.constants.ConversationFileStatus;
import lombok.Data;

import java.util.Date;

@Data
public class FileResourceInfo
{
    private String fileId;
    private String originalFilename;
    private ConversationFileKind kind;
    private ConversationFileStatus status;
    private long statusRevision;
    private String mimeType;
    private long fileSize;
    private String sha256;
    private Integer width;
    private Integer height;
    private String errorCode;
    private String errorMessage;
    private boolean extractionTruncated;
    private Date creationTime;
    private Date modificationTime;
}
