package ifl.agentbreaker.conversationmanager.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "agent-breaker.oss")
@Data
public class OssStorageProperties
{
    private String accessKeyId;
    private String accessKeySecret;
    private String region;
    private String endpoint;
    private String bucketName;
    private String bucketHost;
    private boolean privateBucket = true;
    private int presignedUrlTtlSeconds = 300;
    private String objectPrefix = "dev/user-files";
}
