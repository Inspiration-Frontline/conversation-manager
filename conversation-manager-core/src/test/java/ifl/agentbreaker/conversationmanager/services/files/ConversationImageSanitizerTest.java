package ifl.agentbreaker.conversationmanager.services.files;

import ifl.agentbreaker.conversationmanager.config.ConversationFileProperties;
import ifl.agentbreaker.conversationmanager.domain.entities.pg.FileResource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ConversationImageSanitizerTest
{
    /** Sanitizer under test with deterministic low limits. */
    private ConversationImageSanitizer sanitizer;

    /** Mutable limits used by each test. */
    private ConversationFileProperties properties;

    @BeforeEach
    public void setUp()
    {
        properties = new ConversationFileProperties();
        properties.setMaxSourcePixels(1_000_000);
        properties.setMaxSourceEdgePixels(4_096);
        properties.setMaxModelInputEdgePixels(2_048);
        properties.setImageProcessingConcurrency(1);
        properties.setJpegQuality(0.92F);
        sanitizer = new ConversationImageSanitizer();
        ReflectionTestUtils.setField(sanitizer, "conversationFileProperties", properties);
    }

    @Test
    public void preservesPngAlphaAndScalesWithoutEnlarging() throws Exception
    {
        properties.setMaxModelInputEdgePixels(4);
        BufferedImage source = new BufferedImage(8, 4, BufferedImage.TYPE_INT_ARGB);
        source.setRGB(0, 0, new Color(20, 40, 60, 80).getRGB());

        SanitizedImage result = sanitizer.sanitize(file("png"), encode(source, "png"));
        BufferedImage decoded = ImageIO.read(new java.io.ByteArrayInputStream(result.bytes()));

        assertEquals("image/png", result.mimeType());
        assertEquals(8, result.sourceWidth());
        assertEquals(4, result.sourceHeight());
        assertEquals(4, result.width());
        assertEquals(2, result.height());
        assertNotNull(decoded);
        assertTrue(decoded.getColorModel().hasAlpha());
        assertEquals(64, result.sha256().length());
    }

    @Test
    public void appliesJpegExifOrientationAndRemovesMetadata() throws Exception
    {
        BufferedImage source = new BufferedImage(4, 2, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D graphics = source.createGraphics();
        try
        {
            graphics.fillRect(0, 0, 4, 2);
        }
        finally
        {
            graphics.dispose();
        }
        byte[] orientedJpeg = injectExifOrientation(encode(source, "jpeg"), 6);

        SanitizedImage result = sanitizer.sanitize(file("jpg"), orientedJpeg);

        assertEquals("image/jpeg", result.mimeType());
        assertEquals(2, result.sourceWidth());
        assertEquals(4, result.sourceHeight());
        assertEquals(2, result.width());
        assertEquals(4, result.height());
        assertFalse(new String(result.bytes(), StandardCharsets.ISO_8859_1).contains("Exif"));
    }

    @Test
    public void convertsOpaqueWebpToJpeg() throws Exception
    {
        BufferedImage source = new BufferedImage(3, 2, BufferedImage.TYPE_INT_RGB);
        source.setRGB(1, 1, Color.GREEN.getRGB());
        byte[] webp = encode(source, "webp");

        SanitizedImage result = sanitizer.sanitize(file("webp"), webp);

        assertEquals("image/jpeg", result.mimeType());
        assertEquals("jpg", result.extension());
        assertNotNull(ImageIO.read(new java.io.ByteArrayInputStream(result.bytes())));
    }

    @Test
    public void rejectsKnownAnimationChunksBeforeDecoding()
    {
        byte[] apng = new byte[20];
        System.arraycopy(new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}, 0, apng, 0, 8);
        System.arraycopy("acTL".getBytes(StandardCharsets.US_ASCII), 0, apng, 12, 4);

        FileProcessingException error = assertThrows(
            FileProcessingException.class, () -> sanitizer.sanitize(file("png"), apng));

        assertEquals("ANIMATED_IMAGE_UNSUPPORTED", error.getErrorCode());
    }

    @Test
    public void rejectsMalformedAndOversizedImages() throws Exception
    {
        FileProcessingException malformed = assertThrows(
            FileProcessingException.class, () -> sanitizer.sanitize(file("png"), new byte[] {1, 2, 3}));
        assertEquals("INVALID_IMAGE", malformed.getErrorCode());

        properties.setMaxSourceEdgePixels(3);
        BufferedImage source = new BufferedImage(4, 2, BufferedImage.TYPE_INT_RGB);
        FileProcessingException oversized = assertThrows(
            FileProcessingException.class, () -> sanitizer.sanitize(file("png"), encode(source, "png")));
        assertEquals("IMAGE_DIMENSIONS_EXCEEDED", oversized.getErrorCode());
    }

    private FileResource file(String extension)
    {
        FileResource fileResource = new FileResource();
        fileResource.setOriginalFilename("image." + extension);
        fileResource.setFileExtension(extension);
        return fileResource;
    }

    private byte[] encode(BufferedImage image, String format) throws Exception
    {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, format, output), "ImageIO writer must be available for " + format);
        return output.toByteArray();
    }

    private byte[] injectExifOrientation(byte[] jpeg, int orientation)
    {
        byte[] exif = new byte[] {
            (byte) 0xFF, (byte) 0xE1, 0, 34,
            'E', 'x', 'i', 'f', 0, 0,
            'M', 'M', 0, 42, 0, 0, 0, 8,
            0, 1, 0x01, 0x12, 0, 3, 0, 0, 0, 1,
            0, (byte) orientation, 0, 0,
            0, 0, 0, 0,
        };
        byte[] result = new byte[jpeg.length + exif.length];
        System.arraycopy(jpeg, 0, result, 0, 2);
        System.arraycopy(exif, 0, result, 2, exif.length);
        System.arraycopy(jpeg, 2, result, 2 + exif.length, jpeg.length - 2);
        return result;
    }
}
