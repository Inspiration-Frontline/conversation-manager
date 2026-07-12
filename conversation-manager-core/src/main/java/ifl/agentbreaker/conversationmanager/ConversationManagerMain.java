package ifl.agentbreaker.conversationmanager;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.util.TimeZone;

@SpringBootApplication(scanBasePackages = {"ifl.agentbreaker.conversationmanager", "stark.dataworks.boot.autoconfig"})
@EnableDubbo
@EnableTransactionManagement
public class ConversationManagerMain
{
    public static void main(String[] args)
    {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        org.springframework.boot.SpringApplication.run(ConversationManagerMain.class, args);
    }
}
