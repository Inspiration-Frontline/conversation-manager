package ifl.agentbreaker.conversationmanager.services.rounds;

import com.fasterxml.jackson.databind.ObjectMapper;
import ifl.agentbreaker.commons.api.dto.AgentIdentity;
import ifl.agentbreaker.conversationmanager.rpc.CreateConversationRoundCheckpointRequest;
import ifl.agentbreaker.conversationmanager.rpc.McpServerBindingSnapshot;
import ifl.agentbreaker.conversationmanager.rpc.UserRequest;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class ConversationRoundProgressMapperTest
{
    @Spy private ObjectMapper objectMapper = new ObjectMapper();
    @InjectMocks private ConversationRoundProgressMapper mapper;

    @Test
    void serializesRepeatedProtoMessagesWithoutUnknownFieldInternals()
    {
        CreateConversationRoundCheckpointRequest request = CreateConversationRoundCheckpointRequest.newBuilder()
            .setUserId(7L)
            .setConversationId("conv_phase12")
            .setRoundNumber(1L)
            .setMutationId("mutation-1")
            .setStartTime(1L)
            .setTraceId("trace")
            .setUserRequest(UserRequest.newBuilder().setContent("hello").build())
            .setAgentIdentity(AgentIdentity.newBuilder().setAgentId(1210L).setName("local").setVersion(1).build())
            .addMcpServerBindings(McpServerBindingSnapshot.newBuilder()
                .setServerId("phase12-fixture").setRequired(true).build())
            .build();

        String json = mapper.toCheckpoint(request, "hash").getMcpServerBindings();

        assertEquals("[{\"server_id\":\"phase12-fixture\",\"required\":true}]", json);
        assertTrue(!json.contains("unknownFields"));
    }
}
