package ifl.agentbreaker.conversationmanager.domain.valueobjects;

import ifl.agentbreaker.conversationmanager.domain.constants.ConversationFileKind;
import ifl.agentbreaker.conversationmanager.domain.constants.FileTextExtractionStrategy;
import lombok.Data;

/**
 * Stable schema stored in {@code file_resource.extraction_metadata}.
 *
 * <p>Fields that do not apply to a file format remain {@code null}. Adding a new extractor-specific
 * property requires adding it here, which keeps the JSONB contract discoverable and prevents string
 * keys from being scattered through parser code.</p>
 */
@Data
public class FileExtractionMetadata
{
    private ConversationFileKind kind;
    private String detectedMimeType;
    private Integer width;
    private Integer height;
    private Integer pageCount;
    private Integer paragraphCount;
    private Integer tableCount;
    private Integer sheetCount;
    private Integer slideCount;
    private Integer embeddedImageCount;
    private Integer originalCharacterCount;
    private Integer retainedCharacterCount;
    private FileTextExtractionStrategy textExtractionStrategy;
}
