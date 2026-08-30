package ifl.agentbreaker.conversationmanager.services.files;

import ifl.agentbreaker.conversationmanager.domain.valueobjects.FileExtractionMetadata;

/**
 * Normalized extraction result produced before file-resource persistence.
 *
 * @param detectedMimeType Content type detected from file bytes
 * @param sha256 SHA-256 digest of the source bytes
 * @param extractedText Bounded normalized text, when extraction is supported
 * @param metadata Format-specific extraction metadata
 * @param truncated Whether the text was bounded before persistence
 * @param width Image width when available
 * @param height Image height when available
 */
public record FileExtractionResult(
    String detectedMimeType,
    String sha256,
    String extractedText,
    FileExtractionMetadata metadata,
    boolean truncated,
    Integer width,
    Integer height)
{
}
