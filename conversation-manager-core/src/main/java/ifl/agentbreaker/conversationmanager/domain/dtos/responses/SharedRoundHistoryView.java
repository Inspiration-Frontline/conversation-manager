package ifl.agentbreaker.conversationmanager.domain.dtos.responses;

import java.util.List;

/**
 * Redacted history projection authorized by a Conversation share token.
 *
 * @param latestRoundNumber Frozen latest Round exposed by the share
 * @param rounds Ordered shared Round projections without owner-only evidence
 */
public record SharedRoundHistoryView(long latestRoundNumber, List<RoundView> rounds)
{
    /** One Round in a share-token-authorized, owner-data-redacted history.
     * @param roundNumber stable positive sequence number within the shared snapshot
     * @param userMessage normalized user text submitted for the Round
     * @param assistantAnswer final visible answer, or an empty value for an incomplete Round
     * @param status terminal or in-progress Round status
     * @param errorMessage client-safe failure detail, or an empty value when successful
     * @param turnCount number of persisted model Turns in the Round
     * @param startTime Round start time in epoch milliseconds
     * @param endTime Round end time in epoch milliseconds, or zero while unfinished
     * @param files share-authorized attachments belonging to the Round
     * @param references redacted immutable source boundaries used by the Round
     */
    public record RoundView(long roundNumber, String userMessage, String assistantAnswer, String status,
                            String errorMessage, long turnCount, long startTime, long endTime,
                            List<FileView> files, List<ReferenceView> references)
    {
    }

    /** Share-authorized metadata for one historical attachment.
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

    /** Redacted source boundary that omits the owner's source Conversation ID.
     * @param sourceEndRoundNumber last completed source Round included in the snapshot
     * @param sourceTitle source title captured for display
     */
    public record ReferenceView(long sourceEndRoundNumber, String sourceTitle)
    {
    }
}
