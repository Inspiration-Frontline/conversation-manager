package ifl.agentbreaker.conversationmanager.controllers;

import ifl.agentbreaker.conversationmanager.domain.dtos.requests.ConfirmFileUploadRequest;
import ifl.agentbreaker.conversationmanager.domain.dtos.requests.CreateFileUploadSessionRequest;
import ifl.agentbreaker.conversationmanager.domain.dtos.requests.DeleteFileResourceRequest;
import ifl.agentbreaker.conversationmanager.domain.dtos.requests.RetryFileProcessingRequest;
import ifl.agentbreaker.conversationmanager.domain.dtos.responses.FileDownloadUrl;
import ifl.agentbreaker.conversationmanager.domain.dtos.responses.FileResourceInfo;
import ifl.agentbreaker.conversationmanager.domain.dtos.responses.FileUploadSession;
import ifl.agentbreaker.conversationmanager.services.files.ConversationFileService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import stark.dataworks.boot.web.ServiceResponse;

@RestController
@RequestMapping("/conversation/files")
public class ConversationFileController
{
    @Autowired
    private ConversationFileService conversationFileService;

    @PostMapping("/upload-sessions")
    public ServiceResponse<FileUploadSession> createFileUploadSession(
        @Valid @RequestBody CreateFileUploadSessionRequest request)
    {
        return conversationFileService.createFileUploadSession(request);
    }

    /**
     * Completes a browser-to-OSS upload handshake.
     *
     * <p>The bytes bypass this service and are uploaded through a short-lived signed OSS URL. This
     * endpoint is therefore required to verify that the expected object now exists, validate its
     * size/checksum, atomically move the resource out of PENDING_UPLOAD, and enqueue durable parsing.
     * It does not upload or parse the file in the HTTP request thread.</p>
     */
    @PostMapping("/confirm")
    public ServiceResponse<java.util.List<FileResourceInfo>> confirmFileUpload(
        @Valid @RequestBody ConfirmFileUploadRequest request)
    {
        return conversationFileService.confirmFileUpload(request);
    }

    @GetMapping("/{fileId}")
    public ServiceResponse<FileResourceInfo> getFileResource(@PathVariable String fileId)
    {
        return conversationFileService.getFileResource(fileId);
    }

    /**
     * Requeues a failed asynchronous parser task without forcing the user to upload the same bytes
     * again. Ownership and FAILED state are rechecked so the HTTP action cannot duplicate a running
     * task or retry another user's resource.
     */
    @PostMapping("/retry")
    public ServiceResponse<java.util.List<FileResourceInfo>> retryFileProcessing(
        @Valid @RequestBody RetryFileProcessingRequest request)
    {
        return conversationFileService.retryFileProcessing(request);
    }

    /** Marks one or more owned file resources for logical deletion and asynchronous OSS cleanup. */
    @DeleteMapping
    public ServiceResponse<Boolean> deleteFileResource(@Valid @RequestBody DeleteFileResourceRequest request)
    {
        return conversationFileService.deleteFileResource(request);
    }

    @GetMapping("/{fileId}/download-url")
    public ServiceResponse<FileDownloadUrl> getFileDownloadUrl(@PathVariable String fileId)
    {
        return conversationFileService.getFileDownloadUrl(fileId);
    }
}
