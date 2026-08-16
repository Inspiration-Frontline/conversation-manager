package ifl.agentbreaker.conversationmanager.services.rounds;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import ifl.agentbreaker.conversationmanager.domain.constants.ConversationRoundStatus;
import ifl.agentbreaker.conversationmanager.domain.constants.ToolDispatchState;
import ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationRound;
import ifl.agentbreaker.conversationmanager.domain.entities.pg.ConversationToolDispatch;
import ifl.agentbreaker.conversationmanager.rpc.CreateConversationRoundCheckpointRequest;
import ifl.agentbreaker.conversationmanager.rpc.ContentPart;
import ifl.agentbreaker.conversationmanager.rpc.McpServerBindingSnapshot;
import ifl.agentbreaker.conversationmanager.rpc.RoundStatus;
import ifl.agentbreaker.conversationmanager.rpc.SaveConversationRoundRequest;
import ifl.agentbreaker.conversationmanager.rpc.ToolDispatchEvidence;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
class ConversationRoundProgressMapper
{
    @Autowired private ObjectMapper objectMapper;

    ConversationRound toCheckpoint(CreateConversationRoundCheckpointRequest request, String hash)
    {
        ConversationRound round = new ConversationRound();
        round.setCreatorId(request.getUserId());
        round.setModifierId(request.getUserId());
        round.setConversationId(request.getConversationId());
        round.setRoundNumber(request.getRoundNumber());
        round.setTraceId(request.getTraceId());
        if (request.getUserRequest().getContentPartsCount() > 0)
            round.setUserRequestContentParts(serializeContentParts(request.getUserRequest().getContentPartsList()));
        else
            round.setUserRequestContent(request.getUserRequest().getContent());
        round.setStatus(ConversationRoundStatus.IN_PROGRESS);
        round.setErrorMessage("");
        round.setStartTime(Instant.ofEpochMilli(request.getStartTime()));
        round.setPayloadHashVersion(ConversationRoundPayloadHasher.CURRENT_VERSION);
        round.setPayloadHash(hash);
        round.setAgentId(request.getAgentIdentity().getAgentId());
        round.setAgentName(request.getAgentIdentity().getName());
        round.setAgentVersion(request.getAgentIdentity().getVersion());
        round.setMcpServerBindings(serializeMcpBindings(request.getMcpServerBindingsList()));
        return round;
    }

    SaveConversationRoundRequest toCompatibilityRequest(CreateConversationRoundCheckpointRequest request)
    {
        return SaveConversationRoundRequest.newBuilder()
            .setUserId(request.getUserId()).setConversationId(request.getConversationId())
            .setRoundNumber(request.getRoundNumber()).setUserRequest(request.getUserRequest())
            .addAllReferences(request.getReferencesList()).setStatus(RoundStatus.ROUND_STATUS_IN_PROGRESS)
            .setStartTime(request.getStartTime()).setTraceId(request.getTraceId()).build();
    }

    List<ConversationToolDispatch> toDispatches(long roundId, List<ToolDispatchEvidence> evidence)
    {
        List<ConversationToolDispatch> rows = new ArrayList<>();
        for (ToolDispatchEvidence item : evidence)
        {
            ConversationToolDispatch row = new ConversationToolDispatch();
            row.setRoundId(roundId);
            row.setAttemptId(item.getAttemptId());
            row.setTurnNumber(item.getTurnNumber());
            row.setToolCallId(item.getToolCallId());
            row.setToolName(item.getToolName());
            row.setToolKey(item.getToolKey());
            row.setServerId(item.getServerId());
            row.setArgumentsJson(item.getArgumentsJson());
            row.setState(ToolDispatchState.valueOf(item.getState().name().replace("TOOL_DISPATCH_STATE_", "")));
            row.setDispatchTime(item.getDispatchTime() > 0 ? Instant.ofEpochMilli(item.getDispatchTime()) : null);
            row.setResultTime(item.getResultTime() > 0 ? Instant.ofEpochMilli(item.getResultTime()) : null);
            row.setTraceId(item.getTraceId());
            row.setSpanId(item.getSpanId());
            row.setTransportEvidence(item.getTransportEvidence());
            row.setRecoveryReason(item.getRecoveryReason());
            rows.add(row);
        }
        return rows;
    }

    String serializeContentParts(List<ContentPart> contentParts)
    {
        List<Map<String, Object>> values = new ArrayList<>();
        for (ContentPart contentPart : contentParts)
        {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("type", contentPart.getType());
            if (contentPart.getType().equals("text"))
                value.put("text", contentPart.getText());
            else
            {
                Map<String, Object> file = new LinkedHashMap<>();
                file.put("url", contentPart.getFileUrl().getUrl());
                file.put("detail", contentPart.getFileUrl().getDetail());
                value.put("file_url", file);
            }
            values.add(value);
        }
        return writeJson(values);
    }

    String serializeMcpBindings(List<McpServerBindingSnapshot> bindings)
    {
        List<Map<String, Object>> values = new ArrayList<>();
        for (McpServerBindingSnapshot binding : bindings)
        {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("server_id", binding.getServerId());
            value.put("required", binding.getRequired());
            values.add(value);
        }
        return writeJson(values);
    }

    private String writeJson(Object value)
    {
        try
        {
            return objectMapper.writeValueAsString(value);
        }
        catch (JsonProcessingException e)
        {
            throw new IllegalArgumentException("Unable to serialize incremental Round content.", e);
        }
    }
}
