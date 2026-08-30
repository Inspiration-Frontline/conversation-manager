package ifl.agentbreaker.conversationmanager.services.rounds;

import com.fasterxml.jackson.databind.ObjectMapper;
import ifl.agentbreaker.commons.api.dto.AgentIdentity;
import ifl.agentbreaker.conversationmanager.rpc.CreateConversationRoundCheckpointRequest;
import ifl.agentbreaker.conversationmanager.rpc.McpServerBindingSnapshot;
import ifl.agentbreaker.conversationmanager.rpc.UserRequest;
import ifl.agentbreaker.conversationmanager.support.JsonSerializer;
import ifl.agentbreaker.conversationmanager.domain.valueobjects.McpServerBinding;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConversationRoundProgressMapperTest
{
    /** JSON mapper used to configure the serializer under test. */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** JSON boundary serializer injected into the progress mapper. */
    private final JsonSerializer jsonSerializer = new JsonSerializer();

    /** Progress mapper under test. */
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

        List<McpServerBinding> bindings = mapper.toCheckpoint(request, "hash").getMcpServerBindings();

        assertEquals(1, bindings.size());
        assertEquals("fixture", bindings.getFirst().serverId());
        assertEquals(true, bindings.getFirst().required());
    }
}
