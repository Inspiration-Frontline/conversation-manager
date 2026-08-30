package ifl.agentbreaker.conversationmanager.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/** Creates lifecycle-managed executors for leased asynchronous file processing. */
@Configuration
public class ConversationFileTaskConfiguration
{
    /**
     * Creates the virtual-thread executor used for independent parsing and cleanup work.
     *
     * @return application-owned executor closed by Spring at shutdown
     */
    @Bean(destroyMethod = "shutdown")
    public ExecutorService conversationFileTaskExecutor()
    {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * Creates the scheduler that renews leases while a background task remains active.
     *
     * @return single-threaded scheduler closed by Spring at shutdown
     */
    @Bean(destroyMethod = "shutdown")
    public ScheduledExecutorService conversationFileLeaseExecutor()
    {
        return Executors.newSingleThreadScheduledExecutor();
    }
}
