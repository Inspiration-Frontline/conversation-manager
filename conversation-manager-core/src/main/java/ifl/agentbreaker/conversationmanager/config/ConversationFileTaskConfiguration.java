package ifl.agentbreaker.conversationmanager.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Configuration
public class ConversationFileTaskConfiguration
{
    @Bean(destroyMethod = "shutdown")
    public ExecutorService conversationFileTaskExecutor()
    {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Bean(destroyMethod = "shutdown")
    public ScheduledExecutorService conversationFileLeaseExecutor()
    {
        return Executors.newSingleThreadScheduledExecutor();
    }
}
