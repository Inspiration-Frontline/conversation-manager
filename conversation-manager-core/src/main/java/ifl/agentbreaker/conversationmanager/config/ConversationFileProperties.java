package ifl.agentbreaker.conversationmanager.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import jakarta.annotation.PostConstruct;

/** Validated operational limits for upload, extraction, reservation, and background file work. */
@Component
@ConfigurationProperties(prefix = "agent-breaker.files")
@Data
public class ConversationFileProperties
{
    /** Maximum bytes accepted for one uploaded object. */
    private long maxBytes = 10 * 1024 * 1024;
    /** Maximum number of files frozen into one user request. */
    private int maxCountPerMessage = 5;
    /** Maximum combined bytes of files frozen into one user request. */
    private long maxTotalBytesPerMessage = 50 * 1024 * 1024;
    /** Grace period before an unreferenced file resource becomes eligible for cleanup. */
    private long orphanTtlSeconds = 24 * 60 * 60;
    /** Maximum extracted characters retained as bounded model evidence. */
    private int maxExtractedCharacters = 300_000;
    /** Maximum concurrent background file tasks owned by this process. */
    private int taskConcurrency = 8;
    /** Delay in milliseconds between durable task-queue polls. */
    private int taskPollMilliseconds = 500;
    /** Duration of a processing or cleanup task lease before another worker may recover it. */
    private int taskLeaseSeconds = 300;
    /** Duration for which a prepared file selection remains reserved for one request. */
    private int reservationSeconds = 4 * 60 * 60;
    /** Maximum decoded source pixels accepted before raster allocation. */
    private long maxSourcePixels = 40_000_000;
    /** Maximum source width or height accepted before raster allocation. */
    private int maxSourceEdgePixels = 32_768;
    /** Maximum width or height emitted for a model-input derivative. */
    private int maxModelInputEdgePixels = 2_048;
    /** Maximum number of image rasters processed concurrently. */
    private int imageProcessingConcurrency = 2;
    /** JPEG compression quality used for opaque model-input derivatives. */
    private float jpegQuality = 0.92F;
    /** Normalized filename extensions allowed at upload time. */
    private Set<String> allowedExtensions = new LinkedHashSet<>();
    /** Normalized declared MIME types allowed at upload time. */
    private Set<String> allowedMimeTypes = new LinkedHashSet<>();

    /**
     * Normalizes configured extension and MIME collections once during bean initialization.
     *
     * <p>Configuration may contain leading dots, mixed case, blanks, or duplicates. Normalizing at
     * this boundary lets file validation use stable set membership for every upload.</p>
     */
    @PostConstruct
    public void normalizeConfiguredTypes()
    {
        if (taskPollMilliseconds <= 0 || maxSourcePixels <= 0 || maxSourceEdgePixels <= 0 || maxModelInputEdgePixels <= 0
            || imageProcessingConcurrency <= 0 || jpegQuality <= 0F || jpegQuality > 1F)
            throw new IllegalStateException("Image processing limits must be positive and JPEG quality must be in (0, 1].");
        Set<String> normalizedExtensions = new LinkedHashSet<>();
        for (String extension : allowedExtensions)
        {
            String normalized = extension.trim().toLowerCase(Locale.ROOT);
            while (normalized.startsWith("."))
                normalized = normalized.substring(1);
            if (!normalized.isEmpty())
                normalizedExtensions.add(normalized);
        }
        allowedExtensions = normalizedExtensions;

        Set<String> normalizedMimeTypes = new LinkedHashSet<>();
        for (String mimeType : allowedMimeTypes)
        {
            String normalized = mimeType.trim().toLowerCase(Locale.ROOT);
            if (!normalized.isEmpty())
                normalizedMimeTypes.add(normalized);
        }
        allowedMimeTypes = normalizedMimeTypes;
    }
}
