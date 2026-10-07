package ifl.agentbreaker.conversationmanager.domain.constants;

/**
 * Origin of the single source image of an image-editing Round.
 */
public enum EditSourceKind
{
    /**
     * Source image was uploaded by the user.
     */
    UPLOADED,

    /**
     * Source image was produced by an earlier generation or edit.
     */
    GENERATED
}
