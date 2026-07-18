package ifl.agentbreaker.conversationmanager.services.files;

public record FileExtractionResult(
    String detectedMimeType,
    String sha256,
    String extractedText,
    String metadataJson,
    boolean truncated,
    Integer width,
    Integer height)
{
}
