package ifl.agentbreaker.conversationmanager.dao;

import ifl.agentbreaker.conversationmanager.domain.constants.FileVariantType;
import ifl.agentbreaker.conversationmanager.domain.entities.pg.FileResourceVariant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

/** MyBatis operations for deterministic derivatives of immutable file resources. */
@Mapper
public interface FileResourceVariantMapper
{
    /** Creates or resets one deterministic derivative before its OSS upload.
     * @param fileResourceId original resource identity
     * @param userId owner and audit identity
     * @param variantType derivative purpose
     * @param bucketName target private bucket
     * @param objectKey deterministic target key
     * @return affected row count
     */
    int upsertPending(@Param("fileResourceId") long fileResourceId,
                      @Param("userId") long userId,
                      @Param("variantType") FileVariantType variantType,
                      @Param("bucketName") String bucketName,
                      @Param("objectKey") String objectKey);

    /** Publishes verified derivative metadata.
     * @param fileResourceId original resource identity
     * @param userId owner and audit identity
     * @param variantType derivative purpose
     * @param mimeType verified encoded MIME type
     * @param fileSize encoded byte count
     * @param sha256 lowercase digest
     * @param width verified width
     * @param height verified height
     * @return affected row count
     */
    int markReady(@Param("fileResourceId") long fileResourceId,
                  @Param("userId") long userId,
                  @Param("variantType") FileVariantType variantType,
                  @Param("mimeType") String mimeType,
                  @Param("fileSize") long fileSize,
                  @Param("sha256") String sha256,
                  @Param("width") int width,
                  @Param("height") int height);

    /** Loads one verified derivative.
     * @param fileResourceId original resource identity
     * @param variantType derivative purpose
     * @return ready variant, or {@code null}
     */
    FileResourceVariant getReadyVariant(@Param("fileResourceId") long fileResourceId,
                                        @Param("variantType") FileVariantType variantType);

    /** Loads verified derivatives for an already authorized resource batch.
     * @param fileResourceIds original resource identities
     * @param variantType derivative purpose
     * @return matching derivatives without implied authorization
     */
    List<FileResourceVariant> listReadyVariants(@Param("fileResourceIds") Collection<Long> fileResourceIds,
                                                @Param("variantType") FileVariantType variantType);

    /** Loads every recorded object for bounded cleanup.
     * @param fileResourceId original resource identity
     * @return all derivative rows
     */
    List<FileResourceVariant> listVariants(@Param("fileResourceId") long fileResourceId);

    /** Removes derivative rows after physical object cleanup.
     * @param fileResourceId original resource identity
     * @return affected row count
     */
    int deleteByFileResourceId(@Param("fileResourceId") long fileResourceId);
}
