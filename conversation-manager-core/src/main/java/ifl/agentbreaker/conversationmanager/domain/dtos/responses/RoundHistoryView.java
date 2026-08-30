package ifl.agentbreaker.conversationmanager.domain.dtos.responses;

import java.util.List;

/**
 * Owner-facing history projection for one Conversation.
 *
 * @param conversationId Conversation identity
 * @param latestRoundNumber Durable high-water Round number
 * @param rounds Ordered visible Round projections
 */
public record RoundHistoryView(String conversationId, long latestRoundNumber, List<RoundView> rounds)
{
    /** One owner-visible Round in display order.
     * @param roundNumber stable positive sequence number within the Conversation
     * @param userMessage normalized user text submitted for the Round
     * @param assistantAnswer final visible answer, or an empty value for an incomplete Round
     * @param status terminal or in-progress Round status
     * @param errorMessage client-safe failure detail, or an empty value when successful
     * @param turnCount number of persisted model Turns in the Round
     * @param startTime Round start time in epoch milliseconds
     * @param endTime Round end time in epoch milliseconds, or zero while unfinished
     * @param tools ordered Tool execution projections visible to the owner
     * @param files files attached to the Round
     * @param references immutable source-Conversation boundaries used by the Round
     */
    public record RoundView(long roundNumber, String userMessage, String assistantAnswer, String status,
                            String errorMessage, long turnCount, long startTime, long endTime,
                            List<ToolActivityView> tools, List<FileView> files, List<ReferenceView> references)
    {
    }

    /** Redacted Tool execution evidence shown with a historical Round.
     * @param callId provider-generated call identity
     * @param name provider-visible Tool name used for the call
     * @param toolKey permanent AgentBreaker Tool identity
     * @param arguments normalized JSON arguments after the configured redaction policy
     * @param status terminal execution status
     * @param result normalized result supplied to subsequent model context
     * @param errorMessage failure detail, or an empty value when successful
     */
    public record ToolActivityView(String callId, String name, String toolKey, String arguments,
                                   String status, String result, String errorMessage)
    {
    }

    /** File metadata required to render one historical attachment.
     * @param fileId stable public file identity
     * @param originalFilename safe user-facing filename
     * @param mimeType validated media type
     * @param fileSize persisted byte size
     * @param kind normalized file category
     * @param status current file-processing lifecycle status
     */
    public record FileView(String fileId, String originalFilename, String mimeType, long fileSize,
                           String kind, String status)
    {
    }

    /** Immutable source boundary used to construct the Round context.
     * @param sourceConversationId authorized source Conversation identity
     * @param sourceEndRoundNumber last completed source Round included in the snapshot
     * @param sourceTitle source title captured for display
     */
    public record ReferenceView(String sourceConversationId, long sourceEndRoundNumber, String sourceTitle)
    {
    }
}
