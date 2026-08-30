package ifl.agentbreaker.conversationmanager.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Operational limits applied when one Round freezes evidence from other Conversations. */
@Component
@ConfigurationProperties(prefix = "agent-breaker.references")
@Data
public class ConversationReferenceProperties
{
    /** Maximum distinct Conversation references accepted for one destination Round. */
    private int maxCountPerRound = 10;
}
