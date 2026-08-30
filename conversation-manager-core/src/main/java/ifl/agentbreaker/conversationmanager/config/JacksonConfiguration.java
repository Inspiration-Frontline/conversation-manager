package ifl.agentbreaker.conversationmanager.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JacksonConfiguration
{
    /** Builds the shared UTC-aware mapper used by Conversation Manager persistence and APIs.
     * @return configured mapper with Java time support and ISO-8601 timestamps
     */
    @Bean("conversationManagerObjectMapper")
    public ObjectMapper conversationManagerObjectMapper()
    {
        return JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();
    }
}
