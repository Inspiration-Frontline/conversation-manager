package ifl.agentbreaker.conversationmanager.domain.constants;

/**
 * Method used to choose the single source image of an image-editing Round.
 */
public enum EditResolutionKind
{
    /**
     * Browser selected the source image explicitly.
     */
    EXPLICIT,

    /**
     * The image-reference-resolver Task Agent selected the source image.
     */
    REFERENCE_RESOLVER
}
