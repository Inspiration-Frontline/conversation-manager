package ifl.agentbreaker.conversationmanager.domain.dtos.responses;

/**
 * Authorized provider-input metadata for one image-editing source.
 *
 * @param fileId stable public source image identity
 * @param originalFilename safe display filename
 * @param mimeType MIME type of the exact object the Runner may download
 * @param fileSize byte size of the exact object the Runner may download
 * @param sha256 digest of the exact object the Runner may download, or an empty value when absent
 * @param width verified pixel width of the exact object the Runner may download
 * @param height verified pixel height of the exact object the Runner may download
 * @param origin USER_UPLOAD or GENERATED
 * @param sourceRoundNumber Round that produced or attached the source image
 * @param downloadUrl short-lived signed URL for the exact object
 */
public record PreparedImageEditSource(String fileId, String originalFilename, String mimeType, long fileSize,
                                      String sha256, int width, int height, String origin,
                                      long sourceRoundNumber, String downloadUrl)
{
}
