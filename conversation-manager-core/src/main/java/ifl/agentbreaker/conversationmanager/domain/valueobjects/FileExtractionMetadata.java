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
    /** File kind selected by the parser. */
    private ConversationFileKind kind;
    /** MIME type detected from the uploaded bytes. */
    private String detectedMimeType;
    /** Image width when the file is an image. */
    private Integer width;
    /** Image height when the file is an image. */
    private Integer height;
    /** Numeric page count used for ordering or bounds. */
    private Integer pageCount;
    /** Numeric paragraph count used for ordering or bounds. */
    private Integer paragraphCount;
    /** Numeric table count used for ordering or bounds. */
    private Integer tableCount;
    /** Numeric sheet count used for ordering or bounds. */
    private Integer sheetCount;
    /** Numeric slide count used for ordering or bounds. */
    private Integer slideCount;
    /** Numeric embedded image count used for ordering or bounds. */
    private Integer embeddedImageCount;
    /** Numeric original character count used for ordering or bounds. */
    private Integer originalCharacterCount;
    /** Numeric retained character count used for ordering or bounds. */
    private Integer retainedCharacterCount;
    /** Parser strategy used to produce the retained text. */
    private FileTextExtractionStrategy textExtractionStrategy;
}
