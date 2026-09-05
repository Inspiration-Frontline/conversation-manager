package ifl.agentbreaker.conversationmanager.services.files;

import ifl.agentbreaker.conversationmanager.domain.constants.ConversationFileKind;

import java.util.Map;
import java.util.Locale;
import java.util.Set;

/** Resolves trusted file categories from normalized extensions and validated MIME types. */
public final class ConversationFileTypeResolver
{
    /** Accepted MIME types keyed by normalized file extension. */
    private static final Map<String, Set<String>> MIME_TYPES_BY_EXTENSION = Map.ofEntries(
        Map.entry("docx", Set.of("application/vnd.openxmlformats-officedocument.wordprocessingml.document")),
        Map.entry("pdf", Set.of("application/pdf")),
        Map.entry("xlsx", Set.of("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")),
        Map.entry("pptx", Set.of("application/vnd.openxmlformats-officedocument.presentationml.presentation")),
        Map.entry("txt", Set.of("text/plain")),
        Map.entry("md", Set.of("text/markdown", "text/plain", "text/x-markdown", "text/x-web-markdown")),
        Map.entry("log", Set.of("text/plain")),
        Map.entry("csv", Set.of("text/csv", "application/csv", "text/plain")),
        Map.entry("json", Set.of("application/json", "text/plain")),
        Map.entry("png", Set.of("image/png")),
        Map.entry("jpg", Set.of("image/jpeg")),
        Map.entry("jpeg", Set.of("image/jpeg")),
        Map.entry("webp", Set.of("image/webp")));

    /** Prevents instantiation of the stateless resolver. */
    private ConversationFileTypeResolver()
    {
    }

    /** Normalizes an untrusted filename into a safe display value without changing its extension.
     * @param originalFilename client-supplied filename that may contain path separators
     * @return trimmed final path segment suitable for display and extension validation
     */
    public static String normalizeFilename(String originalFilename)
    {
        String normalized = originalFilename.replace('\\', '/');
        int separator = normalized.lastIndexOf('/');

        if (separator >= 0)
            normalized = normalized.substring(separator + 1);

        return normalized.trim();
    }

    /** Extracts the lowercase filename extension used for format dispatch.
     * @param filename normalized display filename
     * @return lowercase extension without the dot, or an empty value when absent
     */
    public static String getExtension(String filename)
    {
        int dot = filename.lastIndexOf('.');

        if (dot < 0 || dot == filename.length() - 1)
            return "";

        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** Maps an extension to the public file-kind enum used by the UI and model contract.
     * @param extension lowercase validated filename extension
     * @return normalized public file category
     * @throws IllegalArgumentException when the extension is unsupported
     */
    public static ConversationFileKind resolveKind(String extension)
    {
        return switch (extension)
        {
            case "png", "jpg", "jpeg", "webp" -> ConversationFileKind.IMAGE;
            case "xlsx" -> ConversationFileKind.SPREADSHEET;
            case "pptx" -> ConversationFileKind.PRESENTATION;
            case "txt", "md", "log", "csv", "json" -> ConversationFileKind.TEXT;
            case "docx", "pdf" -> ConversationFileKind.DOCUMENT;
            default -> throw new IllegalArgumentException("Unsupported file extension.");
        };
    }

    /** Checks that declared MIME type and extension belong to an accepted format family.
     * @param extension lowercase validated filename extension
     * @param mimeType client-declared MIME type, optionally including parameters
     * @return {@code true} when the normalized MIME type is accepted for the extension
     */
    public static boolean isMimeTypeCompatible(String extension, String mimeType)
    {
        Set<String> allowedMimeTypes = MIME_TYPES_BY_EXTENSION.get(extension);

        if (allowedMimeTypes == null || mimeType == null)
            return false;

        String normalizedMimeType = mimeType.toLowerCase(Locale.ROOT).split(";", 2)[0].trim();
        return allowedMimeTypes.contains(normalizedMimeType);
    }
}
