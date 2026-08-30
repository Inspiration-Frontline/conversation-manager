package ifl.agentbreaker.conversationmanager.services.files;

/** Checked failure carrying a stable code for an upload or extraction error. */
public class FileProcessingException extends Exception
{
    /** Stable file-processing failure code persisted with the file resource. */
    private final String errorCode;

    /** Creates a classified file-processing failure.
     * @param errorCode stable file-processing failure code
     * @param message diagnostic failure explanation
     */
    public FileProcessingException(String errorCode, String message)
    {
        super(message);
        this.errorCode = errorCode;
    }

    /** Creates a classified file-processing failure retaining its underlying cause.
     * @param errorCode stable file-processing failure code
     * @param message diagnostic failure explanation
     * @param cause exception that caused processing to fail
     */
    public FileProcessingException(String errorCode, String message, Throwable cause)
    {
        super(message, cause);
        this.errorCode = errorCode;
    }

    /** Returns the stable failure code stored with the affected file.
     * @return file-processing failure code
     */
    public String getErrorCode()
    {
        return errorCode;
    }
}
