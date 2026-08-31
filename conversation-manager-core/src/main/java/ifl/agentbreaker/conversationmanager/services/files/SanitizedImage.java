package ifl.agentbreaker.conversationmanager.services.files;

/** Verified, metadata-free image derivative ready for deterministic OSS publication.
 * @param bytes encoded derivative bytes
 * @param mimeType encoded MIME type
 * @param extension encoded filename extension
 * @param sha256 lowercase SHA-256 digest
 * @param sourceWidth oriented source width
 * @param sourceHeight oriented source height
 * @param width derivative width
 * @param height derivative height
 */
public record SanitizedImage(byte[] bytes,
                             String mimeType,
                             String extension,
                             String sha256,
                             int sourceWidth,
                             int sourceHeight,
                             int width,
                             int height)
{
}
