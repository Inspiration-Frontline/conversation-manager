package ifl.agentbreaker.conversationmanager.domain.entities.pg;

import ifl.agentbreaker.conversationmanager.domain.constants.FileVariantStatus;
import ifl.agentbreaker.conversationmanager.domain.constants.FileVariantType;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Durable metadata for a server-generated derivative of one immutable file resource. */
@Data
@EqualsAndHashCode(callSuper = true)
public class FileResourceVariant extends EntityBase
{
    /** Database identity of the immutable original resource. */
    private long fileResourceId;
    /** Purpose selecting the only supported consumer contract. */
    private FileVariantType variantType;
    /** Current derivative publication state. */
    private FileVariantStatus status;
    /** Private OSS bucket containing the derivative. */
    private String bucketName;
    /** Deterministic server-generated OSS key. */
    private String objectKey;
    /** MIME type detected after re-encoding. */
    private String mimeType;
    /** Encoded derivative size in bytes. */
    private Long fileSize;
    /** Lowercase SHA-256 digest of the encoded derivative. */
    private String sha256;
    /** Verified derivative width in pixels. */
    private int width;
    /** Verified derivative height in pixels. */
    private int height;
}
