package ifl.agentbreaker.conversationmanager.services.files;

import ifl.agentbreaker.conversationmanager.config.ConversationFileProperties;
import ifl.agentbreaker.conversationmanager.domain.constants.ConversationFileKind;
import ifl.agentbreaker.conversationmanager.domain.constants.FileTextExtractionStrategy;
import ifl.agentbreaker.conversationmanager.domain.entities.pg.FileResource;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Assertions;

public class ConversationFileParserTest
{
    /** Parser under test for bounded text and document extraction. */
    private ConversationFileParser parser;

    /** Extraction limits supplied to the parser for each test case. */
    private ConversationFileProperties properties;

    @BeforeEach
    public void setUp()
    {
        properties = new ConversationFileProperties();
        properties.setMaxBytes(10 * 1024 * 1024);
        properties.setMaxExtractedCharacters(32);
        parser = new ConversationFileParser();
        ReflectionTestUtils.setField(parser, "conversationFileProperties", properties);
    }

    @Test
    public void parsesAndBoundsPlainText() throws Exception
    {
        FileResource fileResource = file("notes.txt", "txt", ConversationFileKind.TEXT);
        FileExtractionResult result = parser.parse(
            fileResource,
            "A deterministic text attachment that exceeds the configured extraction bound."
                .getBytes(StandardCharsets.UTF_8));

        Assertions.assertEquals("text/plain", result.detectedMimeType());
        Assertions.assertTrue(result.truncated());
        Assertions.assertTrue(result.extractedText().contains("Content truncated"));
        Assertions.assertEquals(FileTextExtractionStrategy.BALANCED_EXCERPTS, result.metadata().getTextExtractionStrategy());
        Assertions.assertEquals(64, result.sha256().length());
    }

    @Test
    public void boundedTextRetainsBeginningMiddleAndEndEvidence() throws Exception
    {
        properties.setMaxExtractedCharacters(180);
        String text = "BEGIN-EVIDENCE "
            + "a".repeat(180)
            + " MIDDLE-EVIDENCE "
            + "b".repeat(180)
            + " END-EVIDENCE";

        FileExtractionResult result = parser.parse(
            file("long.txt", "txt", ConversationFileKind.TEXT),
            text.getBytes(StandardCharsets.UTF_8));

        Assertions.assertTrue(result.truncated());
        Assertions.assertTrue(result.extractedText().contains("BEGIN-EVIDENCE"));
        Assertions.assertTrue(result.extractedText().contains("MIDDLE-EVIDENCE"));
        Assertions.assertTrue(result.extractedText().contains("END-EVIDENCE"));
        Assertions.assertEquals(text.length(), result.metadata().getOriginalCharacterCount());
        Assertions.assertEquals(result.extractedText().length(), result.metadata().getRetainedCharacterCount());
    }

    @Test
    public void parsesMarkdownDetectedByTika() throws Exception
    {
        FileExtractionResult result = parser.parse(
            file("notes.md", "md", ConversationFileKind.TEXT),
            "# Heading\n\nMarkdown evidence.".getBytes(StandardCharsets.UTF_8));

        Assertions.assertTrue(ConversationFileTypeResolver.isMimeTypeCompatible("md", result.detectedMimeType()));
        Assertions.assertTrue(result.extractedText().contains("Markdown evidence"));
    }

    @Test
    public void rejectsPdfWithoutReadableTextLayer() throws Exception
    {
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try (PDDocument document = new PDDocument())
        {
            document.addPage(new PDPage());
            document.save(output);
        }

        FileProcessingException error = Assertions.assertThrows(
            FileProcessingException.class,
            () -> parser.parse(file("scan.pdf", "pdf", ConversationFileKind.DOCUMENT), output.toByteArray()));
        Assertions.assertEquals("SCANNED_PDF_UNSUPPORTED", error.getErrorCode());
    }

    @Test
    public void resolvesSupportedFileKinds()
    {
        Assertions.assertEquals(ConversationFileKind.IMAGE, ConversationFileTypeResolver.resolveKind("png"));
        Assertions.assertEquals(ConversationFileKind.SPREADSHEET, ConversationFileTypeResolver.resolveKind("xlsx"));
        Assertions.assertEquals("report.final", ConversationFileTypeResolver.normalizeFilename("C:\\tmp\\report.final"));
        Assertions.assertEquals("", ConversationFileTypeResolver.getExtension("README"));
        Assertions.assertTrue(ConversationFileTypeResolver.isMimeTypeCompatible("md", "text/plain; charset=UTF-8"));
    }

    @Test
    public void rejectsContentThatDoesNotMatchTheExtension()
    {
        FileProcessingException error = Assertions.assertThrows(
            FileProcessingException.class,
            () -> parser.parse(
                file("report.pdf", "pdf", ConversationFileKind.DOCUMENT),
                "This is plain text, not a PDF.".getBytes(StandardCharsets.UTF_8)));

        Assertions.assertEquals("FILE_TYPE_MISMATCH", error.getErrorCode());
    }

    @Test
    public void detectsTheStandardSecurityScanTestSignature()
    {
        FileContentSecurityScanner scanner = new FileContentSecurityScanner();
        byte[] signature = "X5O!P%@AP[4\\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*"
            .getBytes(StandardCharsets.US_ASCII);

        FileProcessingException error = Assertions.assertThrows(FileProcessingException.class, () -> scanner.scan(signature));

        Assertions.assertEquals("MALWARE_DETECTED", error.getErrorCode());
    }

    private FileResource file(String name, String extension, ConversationFileKind kind)
    {
        FileResource fileResource = new FileResource();
        fileResource.setOriginalFilename(name);
        fileResource.setFileExtension(extension);
        fileResource.setKind(kind);

        return fileResource;
    }
}
