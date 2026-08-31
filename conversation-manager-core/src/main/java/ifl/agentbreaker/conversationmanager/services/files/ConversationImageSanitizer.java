package ifl.agentbreaker.conversationmanager.services.files;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import ifl.agentbreaker.conversationmanager.config.ConversationFileProperties;
import ifl.agentbreaker.conversationmanager.domain.entities.pg.FileResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.concurrent.Semaphore;

/** Decodes supported still images under bounded memory and emits metadata-free model input. */
@Component
public class ConversationImageSanitizer
{
    /** Operational image limits and encoder quality. */
    @Autowired
    private ConversationFileProperties conversationFileProperties;

    /** Lazily initialized concurrency boundary for decoded image rasters. */
    private volatile Semaphore concurrency;

    /** Creates a bounded derivative without copying source metadata.
     * @param fileResource verified original metadata
     * @param bytes immutable original bytes
     * @return verified sanitized derivative
     * @throws FileProcessingException when image structure, dimensions, orientation, or encoding is invalid
     */
    public SanitizedImage sanitize(FileResource fileResource, byte[] bytes) throws FileProcessingException
    {
        Semaphore imageConcurrency = getConcurrency();
        boolean acquired = false;
        try
        {
            imageConcurrency.acquire();
            acquired = true;
            rejectKnownAnimation(bytes, fileResource.getFileExtension());
            return decodeAndEncode(fileResource, bytes);
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            throw new FileProcessingException("IMAGE_DERIVATIVE_FAILED", "Image processing was interrupted.", e);
        }
        finally
        {
            if (acquired)
                imageConcurrency.release();
        }
    }

    /** Reads dimensions/frame count before allocating the source raster, then re-encodes pixels only. */
    private SanitizedImage decodeAndEncode(FileResource fileResource, byte[] bytes) throws FileProcessingException
    {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes)))
        {
            if (input == null)
                throw new FileProcessingException("INVALID_IMAGE", "The uploaded image cannot be decoded.");
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext())
                throw new FileProcessingException("INVALID_IMAGE", "The uploaded image cannot be decoded.");
            ImageReader reader = readers.next();
            try
            {
                reader.setInput(input, false, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                validateDimensions(width, height);
                int frameCount = readFrameCount(reader);
                if (frameCount != 1)
                    throw new FileProcessingException(
                        "ANIMATED_IMAGE_UNSUPPORTED", "Animated images are not supported. Upload a still image.");
                BufferedImage decoded = reader.read(0);
                if (decoded == null)
                    throw new FileProcessingException("INVALID_IMAGE", "The uploaded image cannot be decoded.");
                int orientation = readOrientation(fileResource, bytes);
                BufferedImage oriented = applyOrientation(decoded, orientation);
                BufferedImage bounded = scaleDown(oriented);
                boolean pngOutput = shouldUsePng(fileResource, bounded);
                byte[] encoded = encode(bounded, pngOutput);
                BufferedImage verified = ImageIO.read(new ByteArrayInputStream(encoded));
                if (verified == null || verified.getWidth() != bounded.getWidth() || verified.getHeight() != bounded.getHeight())
                    throw new FileProcessingException("IMAGE_DERIVATIVE_FAILED", "The sanitized image could not be verified.");
                String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(encoded));
                return new SanitizedImage(
                    encoded,
                    pngOutput ? "image/png" : "image/jpeg",
                    pngOutput ? "png" : "jpg",
                    digest,
                    oriented.getWidth(),
                    oriented.getHeight(),
                    verified.getWidth(),
                    verified.getHeight());
            }
            finally
            {
                reader.dispose();
            }
        }
        catch (FileProcessingException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            throw new FileProcessingException("INVALID_IMAGE", "The uploaded image cannot be decoded completely.", e);
        }
    }

    /** Rejects dimensions that could allocate an excessive decoded raster. */
    private void validateDimensions(int width, int height) throws FileProcessingException
    {
        long pixels = (long) width * height;
        if (width <= 0 || height <= 0
            || width > conversationFileProperties.getMaxSourceEdgePixels()
            || height > conversationFileProperties.getMaxSourceEdgePixels()
            || pixels > conversationFileProperties.getMaxSourcePixels())
            throw new FileProcessingException(
                "IMAGE_DIMENSIONS_EXCEEDED", "The image dimensions exceed the configured limit.");
    }

    /** Reads decoder frame count and treats unsupported counting as a single-frame format. */
    private int readFrameCount(ImageReader reader) throws Exception
    {
        try
        {
            return reader.getNumImages(true);
        }
        catch (UnsupportedOperationException e)
        {
            return 1;
        }
    }

    /** Reads JPEG EXIF orientation without retaining any source metadata in the output. */
    private int readOrientation(FileResource fileResource, byte[] bytes) throws FileProcessingException
    {
        if (!"jpg".equals(fileResource.getFileExtension()) && !"jpeg".equals(fileResource.getFileExtension()))
            return 1;
        try
        {
            Metadata metadata = ImageMetadataReader.readMetadata(new ByteArrayInputStream(bytes));
            ExifIFD0Directory directory = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
            if (directory == null || !directory.containsTag(ExifIFD0Directory.TAG_ORIENTATION))
                return 1;
            int orientation = directory.getInt(ExifIFD0Directory.TAG_ORIENTATION);
            if (orientation < 1 || orientation > 8)
                throw new FileProcessingException(
                    "IMAGE_ORIENTATION_INVALID", "The image orientation metadata is invalid.");
            return orientation;
        }
        catch (FileProcessingException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            throw new FileProcessingException(
                "IMAGE_ORIENTATION_INVALID", "The image orientation metadata cannot be read safely.", e);
        }
    }

    /** Applies all eight EXIF orientation transforms to pixels. */
    private BufferedImage applyOrientation(BufferedImage source, int orientation)
    {
        int width = source.getWidth();
        int height = source.getHeight();
        boolean swapsAxes = orientation >= 5;
        BufferedImage target = new BufferedImage(
            swapsAxes ? height : width,
            swapsAxes ? width : height,
            source.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        AffineTransform transform = switch (orientation)
        {
            case 2 -> new AffineTransform(-1, 0, 0, 1, width, 0);
            case 3 -> new AffineTransform(-1, 0, 0, -1, width, height);
            case 4 -> new AffineTransform(1, 0, 0, -1, 0, height);
            case 5 -> new AffineTransform(0, 1, 1, 0, 0, 0);
            case 6 -> new AffineTransform(0, 1, -1, 0, height, 0);
            case 7 -> new AffineTransform(0, -1, -1, 0, height, width);
            case 8 -> new AffineTransform(0, -1, 1, 0, 0, width);
            default -> new AffineTransform();
        };
        Graphics2D graphics = target.createGraphics();
        try
        {
            graphics.drawImage(source, transform, null);
        }
        finally
        {
            graphics.dispose();
        }
        return target;
    }

    /** Scales down with bicubic interpolation and never enlarges an image. */
    private BufferedImage scaleDown(BufferedImage source)
    {
        int limit = conversationFileProperties.getMaxModelInputEdgePixels();
        int longest = Math.max(source.getWidth(), source.getHeight());
        if (longest <= limit)
            return source;
        double scale = (double) limit / longest;
        int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
        BufferedImage target = new BufferedImage(
            width, height, source.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = target.createGraphics();
        try
        {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(source, 0, 0, width, height, null);
        }
        finally
        {
            graphics.dispose();
        }
        return target;
    }

    /** Preserves PNG encoding and alpha while converting opaque WebP to JPEG. */
    private boolean shouldUsePng(FileResource fileResource, BufferedImage image)
    {
        return "png".equals(fileResource.getFileExtension()) || image.getColorModel().hasAlpha();
    }

    /** Encodes pixels without source metadata, applying configured JPEG quality when applicable. */
    private byte[] encode(BufferedImage image, boolean pngOutput) throws Exception
    {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (pngOutput)
        {
            if (!ImageIO.write(image, "png", output))
                throw new FileProcessingException("IMAGE_DERIVATIVE_FAILED", "PNG encoding is unavailable.");
            return output.toByteArray();
        }
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext())
            throw new FileProcessingException("IMAGE_DERIVATIVE_FAILED", "JPEG encoding is unavailable.");
        ImageWriter writer = writers.next();
        try (ImageOutputStream imageOutput = ImageIO.createImageOutputStream(output))
        {
            writer.setOutput(imageOutput);
            ImageWriteParam parameters = writer.getDefaultWriteParam();
            parameters.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            parameters.setCompressionQuality(conversationFileProperties.getJpegQuality());
            writer.write(null, new IIOImage(image, null, null), parameters);
        }
        finally
        {
            writer.dispose();
        }
        return output.toByteArray();
    }

    /** Rejects APNG and animated WebP chunks before decoder allocation. */
    private void rejectKnownAnimation(byte[] bytes, String extension) throws FileProcessingException
    {
        if ("png".equals(extension) && containsPngChunk(bytes, "acTL"))
            throw new FileProcessingException(
                "ANIMATED_IMAGE_UNSUPPORTED", "Animated images are not supported. Upload a still image.");
        if ("webp".equals(extension) && (containsWebpChunk(bytes, "ANIM") || containsWebpChunk(bytes, "ANMF")))
            throw new FileProcessingException(
                "ANIMATED_IMAGE_UNSUPPORTED", "Animated images are not supported. Upload a still image.");
    }

    /** Traverses validated PNG chunk boundaries without scanning compressed pixel bytes. */
    private boolean containsPngChunk(byte[] bytes, String marker)
    {
        int offset = 8;
        while (offset <= bytes.length - 12)
        {
            long length = readUnsignedIntBigEndian(bytes, offset);
            if (matchesAscii(bytes, offset + 4, marker))
                return true;
            long next = (long) offset + 12 + length;
            if (next > bytes.length || next <= offset)
                return false;
            offset = (int) next;
        }
        return false;
    }

    /** Traverses RIFF WebP chunk boundaries, including each chunk's even-byte padding. */
    private boolean containsWebpChunk(byte[] bytes, String marker)
    {
        int offset = 12;
        while (offset <= bytes.length - 8)
        {
            if (matchesAscii(bytes, offset, marker))
                return true;
            long length = readUnsignedIntLittleEndian(bytes, offset + 4);
            long next = (long) offset + 8 + length + (length & 1);
            if (next > bytes.length || next <= offset)
                return false;
            offset = (int) next;
        }
        return false;
    }

    /** Compares one four-byte ASCII chunk identifier at a known structural offset. */
    private boolean matchesAscii(byte[] bytes, int offset, String marker)
    {
        if (marker.length() != 4 || offset < 0 || offset > bytes.length - 4)
            return false;
        for (int index = 0; index < 4; index++)
        {
            if (bytes[offset + index] != (byte) marker.charAt(index))
                return false;
        }
        return true;
    }

    /** Reads an unsigned PNG chunk length. */
    private long readUnsignedIntBigEndian(byte[] bytes, int offset)
    {
        return ((long) bytes[offset] & 0xFF) << 24
            | ((long) bytes[offset + 1] & 0xFF) << 16
            | ((long) bytes[offset + 2] & 0xFF) << 8
            | (long) bytes[offset + 3] & 0xFF;
    }

    /** Reads an unsigned RIFF chunk length. */
    private long readUnsignedIntLittleEndian(byte[] bytes, int offset)
    {
        return (long) bytes[offset] & 0xFF
            | ((long) bytes[offset + 1] & 0xFF) << 8
            | ((long) bytes[offset + 2] & 0xFF) << 16
            | ((long) bytes[offset + 3] & 0xFF) << 24;
    }

    /** Creates the configured image semaphore after Spring property binding completes. */
    private Semaphore getConcurrency()
    {
        Semaphore current = concurrency;
        if (current == null)
        {
            synchronized (this)
            {
                current = concurrency;
                if (current == null)
                {
                    current = new Semaphore(conversationFileProperties.getImageProcessingConcurrency());
                    concurrency = current;
                }
            }
        }
        return current;
    }
}
