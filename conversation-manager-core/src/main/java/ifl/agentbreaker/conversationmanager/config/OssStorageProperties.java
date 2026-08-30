package ifl.agentbreaker.conversationmanager.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "agent-breaker.oss")
@Data
public class OssStorageProperties
{
    /** Stable identifier of the access key. */
    private String accessKeyId;
    /** Secret OSS access-key value supplied by deployment configuration. */
    private String accessKeySecret;
    /** OSS region hosting the private bucket. */
    private String region;
    /** OSS API endpoint used by the SDK client. */
    private String endpoint;
    /** Private OSS bucket containing file objects. */
    private String bucketName;
    /** Public host component used to construct signed URLs. */
    private String bucketHost;
    /** Whether the bucket requires signed access for every object. */
    private boolean privateBucket = true;
    /** Lifetime of generated signed upload and download URLs. */
    private int presignedUrlTtlSeconds = 300;
    /** Object-key prefix isolating Conversation Manager files in the bucket. */
    private String objectPrefix = "dev/user-files";
}
