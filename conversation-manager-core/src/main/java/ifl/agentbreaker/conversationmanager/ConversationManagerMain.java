package ifl.agentbreaker.conversationmanager;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"ifl.agentbreaker.conversationmanager", "stark.dataworks.boot.autoconfig"})
@EnableDubbo
public class ConversationManagerMain
{
    public static void main(String[] args)
    {
        org.springframework.boot.SpringApplication.run(ConversationManagerMain.class, args);
    }
}
