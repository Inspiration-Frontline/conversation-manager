package ifl.agentbreaker.conversationmanager.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Centralizes application JSON serialization so every persistence and HTTP projection uses the
 * Spring-configured {@link ObjectMapper} and produces a contextual failure message.
 */
@Component
public class JsonSerializer
{
    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Serializes a value with the application's configured Jackson modules.
     *
     * @param value value to serialize
     * @param subject concise description of the serialized business value
     * @return JSON representation of {@code value}
     * @throws IllegalArgumentException when Jackson cannot serialize the value
     */
    public String serialize(Object value, String subject)
    {
        try
        {
            return objectMapper.writeValueAsString(value);
        }
        catch (JsonProcessingException e)
        {
            throw new IllegalArgumentException(subject + " could not be serialized.", e);
        }
    }

    /**
     * Deserializes JSON into a generic target that retains its Jackson type information.
     *
     * @param json persisted or inbound JSON
     * @param typeReference concrete target type
     * @param subject concise description of the deserialized business value
     * @param <T> target value type
     * @return decoded value
     * @throws IllegalArgumentException when JSON is malformed or incompatible with the target
     */
    public <T> T deserialize(String json, TypeReference<T> typeReference, String subject)
    {
        try
        {
            return objectMapper.readValue(json, typeReference);
        }
        catch (JsonProcessingException e)
        {
            throw new IllegalArgumentException(subject + " could not be deserialized.", e);
        }
    }

    /**
     * Parses JSON into Jackson's tree model for protocol-specific reconstruction.
     *
     * @param json persisted JSON
     * @param subject concise description of the parsed business value
     * @return root JSON node
     * @throws IllegalArgumentException when JSON is malformed
     */
    public JsonNode readTree(String json, String subject)
    {
        try
        {
            return objectMapper.readTree(json);
        }
        catch (JsonProcessingException e)
        {
            throw new IllegalArgumentException(subject + " could not be deserialized.", e);
        }
    }
}
