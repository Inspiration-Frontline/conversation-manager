package ifl.agentbreaker.conversationmanager.services.files;

import ifl.agentbreaker.conversationmanager.domain.entities.pg.FileResource;

/**
 * Builds the immutable OSS key of the sanitized model-input derivative that belongs to one file
 * resource. The key stays beside the resource's original object so uploads and generated outputs
 * share one layout and cleanup only has to walk a single file directory.
 */
public final class DerivativeObjectKeyBuilder
{
    /** Prevents instantiation of this stateless key builder. */
    private DerivativeObjectKeyBuilder()
    {
    }

    /**
     * Builds the deterministic derivative key adjacent to the resource's original object.
     *
     * @param fileResource persisted resource owning the original object key
     * @param extension normalized derivative extension without a leading dot
     * @return derivative object key under the resource's file directory
     */
    public static String build(FileResource fileResource, String extension)
    {
        String objectKey = fileResource.getObjectKey();
        int separator = objectKey.lastIndexOf('/');
        String parent = separator < 0 ? objectKey : objectKey.substring(0, separator);

        return parent + "/derived/model-input." + extension;
    }
}
