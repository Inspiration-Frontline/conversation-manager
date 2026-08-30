package ifl.agentbreaker.conversationmanager.services.rounds;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import ifl.agentbreaker.conversationmanager.domain.constants.LlmMessageRole;
import ifl.agentbreaker.conversationmanager.rpc.ContentPart;
import ifl.agentbreaker.conversationmanager.rpc.FileUrl;
import ifl.agentbreaker.conversationmanager.rpc.LlmConversationMessage;
import ifl.agentbreaker.conversationmanager.rpc.MessageRole;
import ifl.agentbreaker.conversationmanager.rpc.ToolCall;
import ifl.agentbreaker.conversationmanager.support.JsonSerializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the read-optimized request-message copy stored on a Turn without exposing Protobuf
 * implementation fields to Jackson. Normalized request-message tables remain the source of truth.
 */
@Component
class ConversationRequestSnapshotSerializer
{
    /** Shared serializer used for deterministic JSONB request snapshots. */
    @Autowired
    private JsonSerializer jsonSerializer;

    /**
     * Serializes ordered provider-neutral request messages into the stable JSONB projection shape.
     *
     * @param messages messages sent to the model for one Turn
     * @return deterministic JSON array suitable for the Turn snapshot column
     */
    String serialize(List<LlmConversationMessage> messages)
    {
        List<RequestMessageSnapshot> snapshots = new ArrayList<>();
        for (LlmConversationMessage message : messages)
            snapshots.add(toSnapshot(message));
        return jsonSerializer.serialize(snapshots, "Request message snapshot");
    }

    LlmMessageRole mapRole(MessageRole role)
    {
        return switch (role)
        {
            case MESSAGE_ROLE_SYSTEM -> LlmMessageRole.SYSTEM;
            case MESSAGE_ROLE_USER -> LlmMessageRole.USER;
            case MESSAGE_ROLE_ASSISTANT -> LlmMessageRole.ASSISTANT;
            case MESSAGE_ROLE_TOOL -> LlmMessageRole.TOOL;
            case MESSAGE_ROLE_DEVELOPER -> LlmMessageRole.DEVELOPER;
            default -> throw new IllegalArgumentException("Unsupported request message role.");
        };
    }

    /** Converts one provider-neutral request message into the persisted snapshot shape.
     * @param message protobuf request message sent to the model
     * @return JSON-friendly immutable snapshot
     */
    private RequestMessageSnapshot toSnapshot(LlmConversationMessage message)
    {
        return new RequestMessageSnapshot(
            mapRole(message.getRole()),
            StringUtils.hasText(message.getContent()) ? message.getContent() : null,
            toContentParts(message.getContentPartsList()),
            StringUtils.hasText(message.getToolCallId()) ? message.getToolCallId() : null,
            toToolCalls(message.getToolCallsList()));
    }

    /** Converts structured content parts while omitting empty collections.
     * @param contentParts provider-neutral content parts
     * @return immutable snapshots, or {@code null} when no parts exist
     */
    private List<ContentPartSnapshot> toContentParts(List<ContentPart> contentParts)
    {
        if (contentParts.isEmpty())
            return null;
        List<ContentPartSnapshot> snapshots = new ArrayList<>();
        for (ContentPart contentPart : contentParts)
        {
            FileUrlSnapshot fileUrl = null;
            if (!contentPart.getType().equals("text"))
            {
                FileUrl sourceFileUrl = contentPart.getFileUrl();
                fileUrl = new FileUrlSnapshot(sourceFileUrl.getUrl(), sourceFileUrl.getDetail());
            }
            snapshots.add(new ContentPartSnapshot(contentPart.getType(), contentPart.getText(), fileUrl));
        }
        return List.copyOf(snapshots);
    }

    /** Converts embedded provider Tool calls into stable snapshot values.
     * @param toolCalls Tool calls emitted in one request message
     * @return immutable Tool-call snapshots
     */
    private List<ToolCallSnapshot> toToolCalls(List<ToolCall> toolCalls)
    {
        List<ToolCallSnapshot> snapshots = new ArrayList<>();
        for (ToolCall toolCall : toolCalls)
        {
            snapshots.add(new ToolCallSnapshot(
                toolCall.getId(), toolCall.getType(), toolCall.getFunction().getName(),
                toolCall.getFunction().getArguments()));
        }
        return List.copyOf(snapshots);
    }

    /** JSONB projection of one normalized request message.
     * @param role normalized LLM message role
     * @param content optional text content
     */
    @JsonPropertyOrder({"role", "content", "content_parts", "tool_call_id", "tool_calls"})
    private record RequestMessageSnapshot(
        LlmMessageRole role,
        String content,
        @JsonProperty("content_parts") List<ContentPartSnapshot> contentParts,
        @JsonProperty("tool_call_id") String toolCallId,
        @JsonProperty("tool_calls") List<ToolCallSnapshot> toolCalls)
    {
    }

    /** JSONB projection of one structured content part.
     * @param type provider-neutral content discriminator
     * @param text optional text payload
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonPropertyOrder({"type", "text", "file_url"})
    private record ContentPartSnapshot(
        String type,
        String text,
        @JsonProperty("file_url") FileUrlSnapshot fileUrl)
    {
    }

    /** JSONB projection of a provider-bound file URL.
     * @param url signed file URL
     * @param detail optional provider image-detail hint
     */
    @JsonPropertyOrder({"url", "detail"})
    private record FileUrlSnapshot(String url, String detail)
    {
    }

    /** JSONB projection of one model-emitted Tool call.
     * @param id Database or protocol identifier.
     * @param type provider protocol call shape
     */
    @JsonPropertyOrder({"id", "type", "function_name", "arguments"})
    private record ToolCallSnapshot(
        String id,
        String type,
        @JsonProperty("function_name") String functionName,
        String arguments)
    {
    }
}
