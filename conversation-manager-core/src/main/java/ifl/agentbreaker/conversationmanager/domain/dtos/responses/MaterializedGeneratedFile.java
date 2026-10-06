package ifl.agentbreaker.conversationmanager.domain.dtos.responses;

/**
 * Minimal projection of a committed generated-output relation and its durable file identity. Used
 * when replaying an already materialized generation attempt, where loading the full relation and
 * file entities would only duplicate data the caller already holds.
 *
 * @param fileId stable public file identifier returned to the caller
 * @param fileResourceId internal file resource identity returned to the caller
 * @param outputStatus relation presentation status
 */
public record MaterializedGeneratedFile(String fileId, long fileResourceId, String outputStatus)
{
}
