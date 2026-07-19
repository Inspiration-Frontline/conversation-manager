package ifl.agentbreaker.conversationmanager.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import jakarta.annotation.PostConstruct;

@Component
@ConfigurationProperties(prefix = "agent-breaker.files")
@Data
public class ConversationFileProperties
{
    private long maxBytes = 10 * 1024 * 1024;
    private int maxCountPerMessage = 5;
    private long maxTotalBytesPerMessage = 50 * 1024 * 1024;
    private long orphanTtlSeconds = 24 * 60 * 60;
    private int maxExtractedCharacters = 300_000;
    private int taskConcurrency = 8;
    private int taskLeaseSeconds = 300;
    private int reservationSeconds = 4 * 60 * 60;
    private Set<String> allowedExtensions = new LinkedHashSet<>();
    private Set<String> allowedMimeTypes = new LinkedHashSet<>();

    @PostConstruct
    /** Normalizes configured extension and MIME collections once during bean initialization. */
    public void normalizeConfiguredTypes()
    {
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
