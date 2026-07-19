package ifl.agentbreaker.conversationmanager.dao;

import ifl.agentbreaker.conversationmanager.domain.entities.pg.FileResource;
import ifl.agentbreaker.conversationmanager.domain.valueobjects.FileExtractionMetadata;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

@Mapper
public interface FileResourceMapper
{
    FileResource insertFileResource(FileResource fileResource);

    FileResource getFileResourceById(@Param("id") long id);

    FileResource getOwnedFileResource(@Param("fileId") String fileId, @Param("userId") long userId);

    List<FileResource> listOwnedFileResources(@Param("fileIds") Collection<String> fileIds,
                                              @Param("userId") long userId);

    FileResource confirmUpload(@Param("fileId") String fileId,
                               @Param("userId") long userId,
                               @Param("detectedMimeType") String detectedMimeType,
                               @Param("fileSize") long fileSize,
                               @Param("sha256") String sha256);

    int reserveFileResources(@Param("fileIds") Collection<String> fileIds,
                             @Param("userId") long userId,
                             @Param("conversationId") String conversationId,
                             @Param("requestId") String requestId,
                             @Param("reservationSeconds") int reservationSeconds);

    int markProcessing(@Param("id") long id, @Param("userId") long userId);

    int markReady(@Param("id") long id,
                  @Param("userId") long userId,
                  @Param("detectedMimeType") String detectedMimeType,
                  @Param("sha256") String sha256,
                  @Param("extractedText") String extractedText,
                  @Param("extractionMetadata") FileExtractionMetadata extractionMetadata,
                  @Param("extractionTruncated") boolean extractionTruncated,
                  @Param("width") Integer width,
                  @Param("height") Integer height);

    int markFailed(@Param("id") long id,
                   @Param("userId") long userId,
                   @Param("errorCode") String errorCode,
                   @Param("errorMessage") String errorMessage);

    int resetFailedForRetry(@Param("id") long id, @Param("userId") long userId);

    int clearReservationsForConversations(@Param("conversationIds") Collection<String> conversationIds,
                                          @Param("userId") long userId);

    int requestDelete(@Param("fileId") String fileId, @Param("userId") long userId);

    int markDeleted(@Param("id") long id, @Param("userId") long userId);

    boolean hasRoundReferences(@Param("id") long id);
}
