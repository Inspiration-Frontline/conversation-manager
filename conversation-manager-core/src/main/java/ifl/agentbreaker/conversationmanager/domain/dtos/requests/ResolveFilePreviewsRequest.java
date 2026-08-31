package ifl.agentbreaker.conversationmanager.domain.dtos.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** Ordered stable file IDs whose sanitized previews should be resolved as one authorization unit. */
@Data
public class ResolveFilePreviewsRequest
{
    /** Non-empty, unique file IDs in display order. */
    @NotEmpty(message = "At least one file is required.")
    @Size(max = 5, message = "At most five files can be previewed at once.")
    private List<@NotBlank(message = "File IDs cannot be blank.") String> fileIds;
}
