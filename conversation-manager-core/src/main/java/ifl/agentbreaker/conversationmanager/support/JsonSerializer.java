package ifl.agentbreaker.conversationmanager.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Centralizes application JSON serialization so every persistence and HTTP projection uses the
 * Spring-configured {@link ObjectMapper} and produces a contextual failure message.
 */
@Component
public class JsonSerializer
{
    /** Mapper shared with infrastructure type handlers created outside Spring injection. */
    private static ObjectMapper sharedObjectMapper;

    /** Spring-configured mapper used by ordinary service serialization. */
    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Exposes the same configured mapper to MyBatis type handlers, which are instantiated outside
     * ordinary Spring field injection.
     */
    @PostConstruct
    public void registerSharedObjectMapper()
    {
        sharedObjectMapper = objectMapper;
    }

    /**
     * Returns the application-configured mapper for infrastructure adapters that cannot be
     * dependency-injected directly by Spring.
     *
     * @return the single configured Jackson mapper
     * @throws IllegalStateException when called before the Spring application context initializes
     */
    public static ObjectMapper getSharedObjectMapper()
    {
        if (sharedObjectMapper == null)
            throw new IllegalStateException("JsonSerializer has not been initialized.");

        return sharedObjectMapper;
    }

    /**
     * Serializes an infrastructure-owned JSONB value through the same configured mapper as
     * ordinary Spring-managed callers.
     *
     * @param value value to serialize
     * @param subject concise description of the serialized value
     * @return JSON representation of {@code value}
     * @throws IllegalArgumentException when Jackson cannot serialize the value
     */
    public static String serializeShared(Object value, String subject)
    {
        try
        {
            return getSharedObjectMapper().writeValueAsString(value);
        }
        catch (JsonProcessingException e)
        {
            throw new IllegalArgumentException(subject + " could not be serialized.", e);
        }
    }

    /**
     * Deserializes an infrastructure-owned JSONB value with the configured mapper.
     *
     * @param json persisted JSON
     * @param typeReference concrete target type
     * @param subject concise description of the deserialized value
     * @param <T> target value type
     * @return decoded value
     * @throws IllegalArgumentException when JSON is malformed or incompatible with the target
     */
    public static <T> T deserializeShared(String json, TypeReference<T> typeReference, String subject)
    {
        try
        {
            return getSharedObjectMapper().readValue(json, typeReference);
        }
        catch (JsonProcessingException e)
        {
            throw new IllegalArgumentException(subject + " could not be deserialized.", e);
        }
    }

    /**
     * Deserializes an infrastructure-owned JSONB value whose target has no erased generic type.
     *
     * @param json persisted JSON
     * @param targetClass concrete non-generic target class
     * @param subject concise description of the deserialized value
     * @param <T> target value type
     * @return decoded value
     * @throws IllegalArgumentException when JSON is malformed or incompatible with the target
     */
    public static <T> T deserializeShared(String json, Class<T> targetClass, String subject)
    {
        try
        {
            return getSharedObjectMapper().readValue(json, targetClass);
        }
        catch (JsonProcessingException e)
        {
            throw new IllegalArgumentException(subject + " could not be deserialized.", e);
        }
    }

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
