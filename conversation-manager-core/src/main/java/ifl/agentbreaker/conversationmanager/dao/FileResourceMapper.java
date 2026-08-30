package ifl.agentbreaker.conversationmanager.dao;

import ifl.agentbreaker.conversationmanager.domain.entities.pg.FileResource;
import ifl.agentbreaker.conversationmanager.domain.valueobjects.FileExtractionMetadata;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

/** MyBatis operations scoped to the file resource table and its references. */
@Mapper
public interface FileResourceMapper
{
    /** Inserts a newly reserved file resource.
     * @param fileResource validated resource metadata
     * @return inserted entity with generated identity and timestamps
     */
    FileResource insertFileResource(FileResource fileResource);

    /** Loads a file resource by its internal identity.
     * @param id internal database identity
     * @return matching resource, or {@code null} when absent
     */
    FileResource getFileResourceById(@Param("id") long id);

    /** Loads a file resource owned by a user using its public file ID.
     * @param fileId public file identifier
     * @param userId trusted owner identity
     * @return owned resource, or {@code null} when absent
     */
    FileResource getOwnedFileResource(@Param("fileId") String fileId, @Param("userId") long userId);

    /** Loads a resource referenced by a Conversation owned by the user.
     * @param fileId public file identifier
     * @param userId trusted owner identity
     * @return referenced resource, or {@code null} when unauthorized or absent
     */
    FileResource getConversationReferencedFileResource(@Param("fileId") String fileId,
                                                        @Param("userId") long userId);

    /** Loads a bounded set of resources owned by a user.
     * @param fileIds public file identifiers
     * @param userId trusted owner identity
     * @return matching resources, possibly empty
     */
    List<FileResource> listOwnedFileResources(@Param("fileIds") Collection<String> fileIds,
                                              @Param("userId") long userId);

    /** Confirms upload metadata and advances a reserved resource to the uploaded state.
     * @param fileId public file identifier
     * @param userId trusted owner identity
     * @param detectedMimeType content type detected from bytes
     * @param fileSize actual uploaded byte count
     * @param sha256 digest of the uploaded bytes
     * @return updated resource, or {@code null} when the reservation is not owned/valid
     */
    FileResource confirmUpload(@Param("fileId") String fileId,
                               @Param("userId") long userId,
                               @Param("detectedMimeType") String detectedMimeType,
                               @Param("fileSize") long fileSize,
                               @Param("sha256") String sha256);

    /** Reserves several uploaded resources for one Conversation request.
     * @param fileIds public file identifiers
     * @param userId trusted owner identity
     * @param conversationId Conversation receiving the references
     * @param requestId request id used to make reservation idempotent
     * @param reservationSeconds reservation lease duration
     * @return number of resources reserved
     */
    int reserveFileResources(@Param("fileIds") Collection<String> fileIds,
                             @Param("userId") long userId,
                             @Param("conversationId") String conversationId,
                             @Param("requestId") String requestId,
                             @Param("reservationSeconds") int reservationSeconds);

    /** Marks one owned resource as currently being processed.
     * @param id internal resource identity
     * @param userId trusted owner identity
     * @return number of updated rows
     */
    int markProcessing(@Param("id") long id, @Param("userId") long userId);

    /** Stores extracted metadata and marks a resource ready.
     * @param id internal resource identity
     * @param userId trusted owner identity
     * @param detectedMimeType content type detected from bytes
     * @param sha256 digest of the uploaded bytes
     * @param extractedText normalized extracted text, possibly {@code null}
     * @param extractionMetadata typed extraction evidence
     * @param extractionTruncated whether the retained text was bounded
     * @param width image width when applicable
     * @param height image height when applicable
     * @return number of updated rows
     */
    int markReady(@Param("id") long id,
                  @Param("userId") long userId,
                  @Param("detectedMimeType") String detectedMimeType,
                  @Param("sha256") String sha256,
                  @Param("extractedText") String extractedText,
                  @Param("extractionMetadata") FileExtractionMetadata extractionMetadata,
                  @Param("extractionTruncated") boolean extractionTruncated,
                  @Param("width") Integer width,
                  @Param("height") Integer height);

    /** Marks extraction failed and stores a client-safe error classification.
     * @param id internal resource identity
     * @param userId trusted owner identity
     * @param errorCode stable error classification
     * @param errorMessage bounded diagnostic message
     * @return number of updated rows
     */
    int markFailed(@Param("id") long id,
                   @Param("userId") long userId,
                   @Param("errorCode") String errorCode,
                   @Param("errorMessage") String errorMessage);

    /** Reopens a failed resource for another processing attempt.
     * @param id internal resource identity
     * @param userId trusted owner identity
     * @return number of updated rows
     */
    int resetFailedForRetry(@Param("id") long id, @Param("userId") long userId);

    /** Clears expired reservations for the supplied Conversations.
     * @param conversationIds owned Conversation identifiers
     * @param userId trusted owner identity
     * @return number of cleared reservations
     */
    int clearReservationsForConversations(@Param("conversationIds") Collection<String> conversationIds,
                                          @Param("userId") long userId);

    /** Marks an owned resource for deletion and leaves physical cleanup to a task worker.
     * @param fileId public file identifier
     * @param userId trusted owner identity
     * @return number of updated rows
     */
    int requestDelete(@Param("fileId") String fileId, @Param("userId") long userId);

    /** Records successful physical deletion for one owned resource.
     * @param id internal resource identity
     * @param userId trusted owner identity
     * @return number of updated rows
     */
    int markDeleted(@Param("id") long id, @Param("userId") long userId);

    /** Checks whether any non-deleted Round still references a resource.
     * @param id internal resource identity
     * @return {@code true} when at least one reference exists
     */
    boolean hasRoundReferences(@Param("id") long id);
}
