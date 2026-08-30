package ifl.agentbreaker.conversationmanager.services.rounds;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ifl.agentbreaker.conversationmanager.config.JacksonConfiguration;
import ifl.agentbreaker.conversationmanager.rpc.FunctionCall;
import ifl.agentbreaker.conversationmanager.rpc.LlmConversationMessage;
import ifl.agentbreaker.conversationmanager.rpc.MessageRole;
import ifl.agentbreaker.conversationmanager.rpc.ToolCall;
import ifl.agentbreaker.conversationmanager.support.JsonSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConversationRequestSnapshotSerializerTest
{
    /** Jackson mapper configured with the same modules as the Conversation Manager service. */
    private final ObjectMapper objectMapper = new JacksonConfiguration().conversationManagerObjectMapper();

    /** Snapshot serializer under test. */
    private final ConversationRequestSnapshotSerializer serializer = new ConversationRequestSnapshotSerializer();

    @BeforeEach
    void configureSerializer()
    {
        JsonSerializer jsonSerializer = new JsonSerializer();
        ReflectionTestUtils.setField(jsonSerializer, "objectMapper", objectMapper);
        ReflectionTestUtils.setField(serializer, "jsonSerializer", jsonSerializer);
    }

    @Test
    void serializesNormalizedMessagesWithoutProtobufImplementationFields()
        throws Exception
    {
        LlmConversationMessage systemMessage = LlmConversationMessage.newBuilder()
            .setRole(MessageRole.MESSAGE_ROLE_SYSTEM)
            .setContent("Use connected services when needed.")
            .build();
        LlmConversationMessage assistantMessage = LlmConversationMessage.newBuilder()
            .setRole(MessageRole.MESSAGE_ROLE_ASSISTANT)
            .addToolCalls(ToolCall.newBuilder()
                .setId("call-1")
                .setType("function")
                .setFunction(FunctionCall.newBuilder().setName("search").setArguments("{\"query\":\"Python\"}")))
            .build();

        JsonNode snapshot = objectMapper.readTree(serializer.serialize(List.of(systemMessage, assistantMessage)));

        assertEquals("SYSTEM", snapshot.get(0).get("role").asText());
        assertEquals("Use connected services when needed.", snapshot.get(0).get("content").asText());
        assertTrue(snapshot.get(0).get("content_parts").isNull());
        assertEquals(0, snapshot.get(0).get("tool_calls").size());
        assertEquals("call-1", snapshot.get(1).get("tool_calls").get(0).get("id").asText());
        assertEquals("search", snapshot.get(1).get("tool_calls").get(0).get("function_name").asText());
        assertEquals("{\"query\":\"Python\"}",
            snapshot.get(1).get("tool_calls").get(0).get("arguments").asText());
    }
}
