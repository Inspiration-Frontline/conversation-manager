package ifl.agentbreaker.conversationmanager.services.files;

import ifl.agentbreaker.conversationmanager.config.ConversationFileProperties;
import ifl.agentbreaker.conversationmanager.domain.constants.ConversationFileKind;
import ifl.agentbreaker.conversationmanager.domain.constants.FileTextExtractionStrategy;
import ifl.agentbreaker.conversationmanager.domain.entities.pg.FileResource;
import ifl.agentbreaker.conversationmanager.domain.valueobjects.FileExtractionMetadata;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFPictureData;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.security.MessageDigest;
import java.util.HexFormat;

@Component
public class ConversationFileParser
{
    private static final String TRUNCATION_MARKER = "\n\n[Content truncated here by the configured extraction limit.]\n\n";

    @Autowired
    private ConversationFileProperties properties;

    private final Tika tika = new Tika();

    /**
     * Validates the immutable uploaded bytes and extracts a bounded, provenance-preserving text
     * representation for Agent Runner. This method never executes active Office content or performs
     * OCR. A returned result is safe to persist only after the caller's security scan has passed.
     */
    public FileExtractionResult parse(FileResource fileResource, byte[] bytes) throws FileProcessingException
    {
        try
        {
            // Detection uses file bytes plus the original filename; the declared browser MIME type
            // is not trusted because it is supplied by the client.
            String detectedMimeType = tika.detect(bytes, fileResource.getOriginalFilename());
            if (!ConversationFileTypeResolver.isMimeTypeCompatible(
                fileResource.getFileExtension(), detectedMimeType))
                throw new FileProcessingException(
                    "FILE_TYPE_MISMATCH",
                    "The uploaded file content does not match its filename extension.");

            // Recompute SHA-256 from the OSS bytes so confirmation and asynchronous processing are
            // tied to exactly the same immutable payload.
            String sha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            if (fileResource.getSha256() != null && !fileResource.getSha256().equalsIgnoreCase(sha256))
                throw new FileProcessingException("CHECKSUM_MISMATCH", "The uploaded file checksum does not match.");

            FileExtractionMetadata metadata = new FileExtractionMetadata();
            metadata.setKind(fileResource.getKind());
            metadata.setDetectedMimeType(detectedMimeType);

            // Images keep dimensions and are sent to a vision-capable model through a signed URL.
            // Document families instead produce stable text with explicit page/sheet/slide markers.
            String extractedText = "";
            Integer width = null;
            Integer height = null;
            if (fileResource.getKind() == ConversationFileKind.IMAGE)
            {
                BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
                if (image != null)
                {
                    width = image.getWidth();
                    height = image.getHeight();
                    metadata.setWidth(width);
                    metadata.setHeight(height);
                }
                else if (!"image/webp".equalsIgnoreCase(detectedMimeType))
                    throw new FileProcessingException("INVALID_IMAGE", "The uploaded image cannot be decoded.");
            }
            else
                extractedText = extractText(fileResource, bytes, metadata);

            // This is deliberately bounded context for chat-time analysis. Large documents are not
            // losslessly searchable yet; retain representative excerpts until Knowledge Manager
            // adds chunk storage, retrieval, and provenance-aware citations.
            TruncatedText truncatedText = retainTextWithinLimit(extractedText);
            metadata.setOriginalCharacterCount(extractedText.length());
            metadata.setRetainedCharacterCount(truncatedText.text().length());
            metadata.setTextExtractionStrategy(truncatedText.truncated()
                ? FileTextExtractionStrategy.BALANCED_EXCERPTS
                : FileTextExtractionStrategy.FULL_TEXT);
            return new FileExtractionResult(
                detectedMimeType,
                sha256,
                truncatedText.text(),
                metadata,
                truncatedText.truncated(),
                width,
                height);
        }
        catch (FileProcessingException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            throw new FileProcessingException("FILE_PROCESSING_FAILED", "The file could not be processed.", e);
        }
    }

    /**
     * Apache Tika is intentionally used for MIME detection, not {@code parseToString()} extraction.
     * The format-specific readers below preserve evidence boundaries that generic Tika text flattens:
     * PDF page numbers, DOCX headings/lists/tables, XLSX sheet/cell/formula coordinates, and PPTX
     * slides/notes/embedded-image inventory. They also enforce strict UTF-8 for plain-text files.
     */
    private String extractText(
        FileResource fileResource,
        byte[] bytes,
        FileExtractionMetadata metadata) throws Exception
    {
        return switch (fileResource.getFileExtension())
        {
            case "pdf" -> extractPdf(bytes, metadata);
            case "docx" -> extractDocx(bytes, metadata);
            case "xlsx" -> extractXlsx(bytes, metadata);
            case "pptx" -> extractPptx(bytes, metadata);
            case "txt", "md", "log", "csv", "json" -> decodeUtf8(bytes);
            default -> throw new FileProcessingException("UNSUPPORTED_FILE_TYPE", "The file type is not supported.");
        };
    }

    private String extractPdf(byte[] bytes, FileExtractionMetadata metadata) throws Exception
    {
        try (PDDocument document = Loader.loadPDF(bytes))
        {
            metadata.setPageCount(document.getNumberOfPages());
            PDFTextStripper stripper = new PDFTextStripper();
            StringBuilder builder = new StringBuilder();
            for (int page = 1; page <= document.getNumberOfPages(); page++)
            {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String pageText = stripper.getText(document).trim();
                if (!pageText.isEmpty())
                    builder.append("\n\n[Page ").append(page).append("]\n").append(pageText);
            }
            if (builder.toString().isBlank())
                throw new FileProcessingException(
                    "SCANNED_PDF_UNSUPPORTED",
                    "Scanned PDFs without a readable text layer are not supported in the current version.");
            return builder.toString().trim();
        }
    }

    private String extractDocx(byte[] bytes, FileExtractionMetadata metadata) throws Exception
    {
        configureZipSafety();
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes)))
        {
            metadata.setParagraphCount(document.getParagraphs().size());
            metadata.setTableCount(document.getTables().size());
            StringBuilder builder = new StringBuilder();
            for (XWPFParagraph paragraph : document.getParagraphs())
            {
                String text = paragraph.getText().trim();
                if (!text.isEmpty())
                {
                    String style = paragraph.getStyle();
                    if (style != null && style.toLowerCase().startsWith("heading"))
                        builder.append('[').append(style).append("] ");
                    else if (paragraph.getNumID() != null)
                        builder.append("[List] ");
                    builder.append(text).append('\n');
                }
            }
            int tableNumber = 0;
            for (XWPFTable table : document.getTables())
            {
                tableNumber++;
                builder.append("\n[Table ").append(tableNumber).append("]\n");
                table.getRows().forEach(row -> builder.append(
                    String.join(" | ", row.getTableCells().stream().map(cell -> cell.getText().trim()).toList()))
                    .append('\n'));
            }
            return builder.toString().trim();
        }
    }

    private String extractXlsx(byte[] bytes, FileExtractionMetadata metadata) throws Exception
    {
        configureZipSafety();
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes)))
        {
            metadata.setSheetCount(workbook.getNumberOfSheets());
            DataFormatter formatter = new DataFormatter();
            formatter.setUseCachedValuesForFormulaCells(true);
            StringBuilder builder = new StringBuilder();
            for (Sheet sheet : workbook)
            {
                builder.append("\n\n[Sheet: ").append(sheet.getSheetName()).append("]\n");
                for (Row row : sheet)
                {
                    for (Cell cell : row)
                    {
                        String value = formatter.formatCellValue(cell);
                        if (!value.isBlank())
                        {
                            builder.append(cell.getAddress().formatAsString()).append(": ").append(value);
                            if (cell.getCellType() == CellType.FORMULA)
                                builder.append(" [formula: ").append(cell.getCellFormula()).append(']');
                            builder.append('\n');
                        }
                    }
                }
            }
            return builder.toString().trim();
        }
    }

    private String extractPptx(byte[] bytes, FileExtractionMetadata metadata) throws Exception
    {
        configureZipSafety();
        try (XMLSlideShow slideShow = new XMLSlideShow(new ByteArrayInputStream(bytes)))
        {
            metadata.setSlideCount(slideShow.getSlides().size());
            metadata.setEmbeddedImageCount(slideShow.getPictureData().size());
            StringBuilder builder = new StringBuilder();
            int slideNumber = 0;
            for (XSLFSlide slide : slideShow.getSlides())
            {
                slideNumber++;
                builder.append("\n\n[Slide ").append(slideNumber).append("]\n");
                appendTextShapes(builder, slide.getShapes());
                if (slide.getNotes() != null)
                {
                    builder.append("[Notes]\n");
                    appendTextShapes(builder, slide.getNotes().getShapes());
                }
            }
            if (!slideShow.getPictureData().isEmpty())
            {
                builder.append("\n\n[Embedded images]\n");
                for (XSLFPictureData pictureData : slideShow.getPictureData())
                    builder.append(pictureData.getFileName())
                        .append(" (")
                        .append(pictureData.getContentType())
                        .append(")\n");
            }
            return builder.toString().trim();
        }
    }

    private void appendTextShapes(StringBuilder builder, Iterable<XSLFShape> shapes)
    {
        for (XSLFShape shape : shapes)
        {
            if (shape instanceof XSLFTextShape textShape)
            {
                String text = textShape.getText().trim();
                if (!text.isEmpty())
                    builder.append(text).append('\n');
            }
        }
    }

    private String decodeUtf8(byte[] bytes) throws FileProcessingException
    {
        try
        {
            return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString();
        }
        catch (CharacterCodingException e)
        {
            throw new FileProcessingException("INVALID_TEXT_ENCODING", "Text files must use UTF-8 encoding.", e);
        }
    }

    /**
     * Keeps evidence from the beginning, middle, and end instead of retaining only chapter one.
     * This is still a bounded context representation rather than lossless document storage; future
     * Knowledge Manager ingestion should chunk and retrieve the complete document when required.
     */
    private TruncatedText retainTextWithinLimit(String text)
    {
        int maximum = properties.getMaxExtractedCharacters();
        if (text.length() <= maximum)
            return new TruncatedText(text, false);

        int markerCharacters = TRUNCATION_MARKER.length() * 2;
        if (maximum <= markerCharacters + 3)
            return new TruncatedText(text.substring(0, maximum) + TRUNCATION_MARKER, true);

        int retainedCharacters = maximum - markerCharacters;
        int headLength = retainedCharacters / 3;
        int middleLength = retainedCharacters / 3;
        int tailLength = retainedCharacters - headLength - middleLength;
        int middleStart = Math.max(headLength, (text.length() - middleLength) / 2);
        int tailStart = text.length() - tailLength;
        return new TruncatedText(
            text.substring(0, headLength)
                + TRUNCATION_MARKER
                + text.substring(middleStart, middleStart + middleLength)
                + TRUNCATION_MARKER
                + text.substring(tailStart),
            true);
    }

    private void configureZipSafety()
    {
        ZipSecureFile.setMinInflateRatio(0.01);
        ZipSecureFile.setMaxEntrySize(properties.getMaxBytes() * 20);
        ZipSecureFile.setMaxTextSize(properties.getMaxExtractedCharacters() * 4L);
    }

    private record TruncatedText(String text, boolean truncated)
    {
    }
}
