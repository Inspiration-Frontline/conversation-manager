package ifl.agentbreaker.conversationmanager.dao.typehandlers;

import ifl.agentbreaker.conversationmanager.domain.valueobjects.FileExtractionMetadata;

/** Converts the typed extraction metadata value object to and from PostgreSQL JSONB. */
public class FileExtractionMetadataTypeHandler extends JsonbTypeHandler<FileExtractionMetadata>
{
    /** Creates a JSONB handler for the non-generic extraction metadata value object. */
    public FileExtractionMetadataTypeHandler()
    {
        super(FileExtractionMetadata.class, "File extraction metadata", null);
    }
}
