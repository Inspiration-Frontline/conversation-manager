package ifl.agentbreaker.conversationmanager.services.rounds;

import com.fasterxml.jackson.databind.ObjectMapper;
import ifl.agentbreaker.commons.api.dto.AgentIdentity;
import ifl.agentbreaker.conversationmanager.rpc.CreateConversationRoundCheckpointRequest;
import ifl.agentbreaker.conversationmanager.rpc.McpServerBindingSnapshot;
import ifl.agentbreaker.conversationmanager.rpc.UserRequest;
import ifl.agentbreaker.conversationmanager.support.JsonSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConversationRoundProgressMapperTest
{
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JsonSerializer jsonSerializer = new JsonSerializer();
    private final ConversationRoundProgressMapper mapper = new ConversationRoundProgressMapper();

    @BeforeEach
    void configureSerializer()
    {
        ReflectionTestUtils.setField(jsonSerializer, "objectMapper", objectMapper);
        ReflectionTestUtils.setField(mapper, "jsonSerializer", jsonSerializer);
    }

    @Test
    void serializesRepeatedProtoMessagesWithoutUnknownFieldInternals()
    {
        CreateConversationRoundCheckpointRequest request = CreateConversationRoundCheckpointRequest.newBuilder()
            .setUserId(7L)
            .setConversationId("conv_test")
            .setRoundNumber(1L)
            .setMutationId("mutation-1")
            .setStartTime(1L)
            .setTraceId("trace")
            .setUserRequest(UserRequest.newBuilder().setContent("hello").build())
            .setAgentIdentity(AgentIdentity.newBuilder().setAgentId(1210L).setName("local").setVersion(1).build())
            .addMcpServerBindings(McpServerBindingSnapshot.newBuilder()
                .setServerId("fixture").setRequired(true).build())
            .build();

        String json = mapper.toCheckpoint(request, "hash").getMcpServerBindings();

        assertEquals("[{\"server_id\":\"fixture\",\"required\":true}]", json);
        assertTrue(!json.contains("unknownFields"));
    }
}
