package ifl.agentbreaker.conversationmanager.services.files;

import com.fasterxml.jackson.databind.ObjectMapper;
import ifl.agentbreaker.conversationmanager.config.ConversationFileProperties;
import ifl.agentbreaker.conversationmanager.domain.constants.ConversationFileKind;
import ifl.agentbreaker.conversationmanager.domain.entities.pg.FileResource;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ConversationFileParserTest
{
    private ConversationFileParser parser;

    @BeforeEach
    public void setUp()
    {
        ConversationFileProperties properties = new ConversationFileProperties();
        properties.setMaxBytes(10 * 1024 * 1024);
        properties.setMaxExtractedCharacters(32);
        parser = new ConversationFileParser();
        ReflectionTestUtils.setField(parser, "properties", properties);
        ReflectionTestUtils.setField(parser, "objectMapper", new ObjectMapper());
    }

    @Test
    public void parsesAndBoundsPlainText() throws Exception
    {
        FileResource fileResource = file("notes.txt", "txt", ConversationFileKind.TEXT);
        FileExtractionResult result = parser.parse(
            fileResource,
            "A deterministic text attachment that exceeds the configured extraction bound."
                .getBytes(StandardCharsets.UTF_8));

        assertEquals("text/plain", result.detectedMimeType());
        assertTrue(result.truncated());
        assertTrue(result.extractedText().contains("Content truncated"));
        assertEquals(64, result.sha256().length());
    }

    @Test
    public void parsesMarkdownDetectedByTika() throws Exception
    {
        FileExtractionResult result = parser.parse(
            file("notes.md", "md", ConversationFileKind.TEXT),
            "# Heading\n\nMarkdown evidence.".getBytes(StandardCharsets.UTF_8));

        assertTrue(ConversationFileTypeResolver.isMimeTypeCompatible("md", result.detectedMimeType()));
        assertTrue(result.extractedText().contains("Markdown evidence"));
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

        FileProcessingException error = assertThrows(
            FileProcessingException.class,
            () -> parser.parse(file("scan.pdf", "pdf", ConversationFileKind.DOCUMENT), output.toByteArray()));
        assertEquals("SCANNED_PDF_UNSUPPORTED", error.getErrorCode());
    }

    @Test
    public void resolvesSupportedFileKinds()
    {
        assertEquals(ConversationFileKind.IMAGE, ConversationFileTypeResolver.resolveKind("png"));
        assertEquals(ConversationFileKind.SPREADSHEET, ConversationFileTypeResolver.resolveKind("xlsx"));
        assertEquals("report.final", ConversationFileTypeResolver.normalizeFilename("C:\\tmp\\report.final"));
        assertEquals("", ConversationFileTypeResolver.getExtension("README"));
        assertTrue(ConversationFileTypeResolver.isMimeTypeCompatible("md", "text/plain; charset=UTF-8"));
    }

    @Test
    public void rejectsContentThatDoesNotMatchTheExtension()
    {
        FileProcessingException error = assertThrows(
            FileProcessingException.class,
            () -> parser.parse(
                file("report.pdf", "pdf", ConversationFileKind.DOCUMENT),
                "This is plain text, not a PDF.".getBytes(StandardCharsets.UTF_8)));

        assertEquals("FILE_TYPE_MISMATCH", error.getErrorCode());
    }

    @Test
    public void detectsTheStandardSecurityScanTestSignature()
    {
        FileContentSecurityScanner scanner = new FileContentSecurityScanner();
        byte[] signature = "X5O!P%@AP[4\\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*"
            .getBytes(StandardCharsets.US_ASCII);

        FileProcessingException error = assertThrows(FileProcessingException.class, () -> scanner.scan(signature));

        assertEquals("MALWARE_DETECTED", error.getErrorCode());
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
