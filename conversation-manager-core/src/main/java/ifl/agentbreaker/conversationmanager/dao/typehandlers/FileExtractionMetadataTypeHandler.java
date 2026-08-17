package ifl.agentbreaker.conversationmanager.dao.typehandlers;

import com.fasterxml.jackson.core.type.TypeReference;
import ifl.agentbreaker.conversationmanager.domain.valueobjects.FileExtractionMetadata;

/** Converts the typed extraction metadata value object to and from PostgreSQL JSONB. */
public class FileExtractionMetadataTypeHandler extends JsonbTypeHandler<FileExtractionMetadata>
{
    private static final TypeReference<FileExtractionMetadata> TYPE = new TypeReference<>() { };

    @Override
    protected TypeReference<FileExtractionMetadata> getTypeReference()
    {
        return TYPE;
    }

    @Override
    protected String getSubject()
    {
        return "File extraction metadata";
    }

    @Override
    protected FileExtractionMetadata getEmptyValue()
    {
        return null;
    }
}
