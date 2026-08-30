package ifl.agentbreaker.conversationmanager;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.util.TimeZone;

/** Boots the HTTP, Dubbo, scheduling, and transaction-managed Conversation Manager service. */
@SpringBootApplication(scanBasePackages = {"ifl.agentbreaker.conversationmanager", "stark.dataworks.boot.autoconfig"})
@EnableDubbo
@EnableTransactionManagement
@EnableScheduling
public class ConversationManagerMain
{
    /**
     * Starts Conversation Manager after fixing the process default timezone to UTC.
     *
     * @param args Spring Boot command-line arguments
     */
    public static void main(String[] args)
    {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(ConversationManagerMain.class, args);
    }
}
