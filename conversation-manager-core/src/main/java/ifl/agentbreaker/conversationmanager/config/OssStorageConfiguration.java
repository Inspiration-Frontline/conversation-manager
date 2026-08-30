package ifl.agentbreaker.conversationmanager.config;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OssStorageConfiguration
{
    /** Creates the shared OSS client used by file upload and download services.
     * @param properties validated OSS endpoint and credential settings
     * @return client configured for the current storage endpoint
     */
    @Bean(destroyMethod = "shutdown")
    public OSS ossClient(OssStorageProperties properties)
    {
        return new OSSClientBuilder().build(
            properties.getEndpoint(),
            properties.getAccessKeyId(),
            properties.getAccessKeySecret());
    }
}
