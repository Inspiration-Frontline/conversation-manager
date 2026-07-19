package ifl.agentbreaker.conversationmanager.services.files;

import ifl.agentbreaker.conversationmanager.domain.valueobjects.FileExtractionMetadata;

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
