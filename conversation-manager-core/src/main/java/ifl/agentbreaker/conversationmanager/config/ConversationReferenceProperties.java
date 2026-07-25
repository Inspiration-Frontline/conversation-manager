package ifl.agentbreaker.conversationmanager.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "agent-breaker.references")
@Data
public class ConversationReferenceProperties
{
    private int maxCountPerRound = 10;
}
