package ifl.agentbreaker.conversationmanager.config;

import ifl.agentbreaker.authcenter.session.NotLoggedInException;
import ifl.agentbreaker.conversationmanager.exceptions.ServiceResponseException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import stark.dataworks.boot.web.ServiceResponse;

import java.util.stream.Collectors;

/** Maps validation, authentication, and unexpected failures to the service response contract. */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler
{
    /** Client-visible code for malformed or invalid request input. */
    private static final int ERROR_BAD_REQUEST = -100;
    /** Client-visible code for an unexpected server failure. */
    private static final int ERROR_INTERNAL = -200;
    /** Authentication code used when the shared session is absent. */
    private static final int ERROR_NOT_LOGGED_IN = 2001;

    /** Converts a missing-login failure to a stable client response.
     * @param e authentication exception raised by the shared session boundary
     * @return service error response with the not-logged-in code
     */
    @ExceptionHandler({NotLoggedInException.class})
    public ServiceResponse<?> handleNotLoggedInException(NotLoggedInException e)
    {
        log.warn("Request is not logged in.");
        return ServiceResponse.buildErrorResponse(ERROR_NOT_LOGGED_IN, e.getMessage());
    }

    /** Preserves a business error response raised by a service method.
     * @param e classified service failure
     * @return service error response carrying the original code and message
     */
    @ExceptionHandler({ServiceResponseException.class})
    public ServiceResponse<?> handleServiceResponseException(ServiceResponseException e)
    {
        log.warn("Business request failed: {}", e.getMessage());
        return ServiceResponse.buildErrorResponse(e.getCode(), e.getMessage());
    }

    /** Converts bean-validation failures into a field-oriented client message.
     * @param e validation failure containing rejected fields
     * @return bad-request response with joined field messages
     */
    @ExceptionHandler({MethodArgumentNotValidException.class})
    public ServiceResponse<?> handleMethodArgumentNotValidException(MethodArgumentNotValidException e)
    {
        log.warn("Request validation failed.", e);
        return ServiceResponse.buildErrorResponse(ERROR_BAD_REQUEST, getBindingErrorMessage(e));
    }

    /** Converts request-binding failures into a field-oriented client message.
     * @param e binding failure containing rejected fields
     * @return bad-request response with joined field messages
     */
    @ExceptionHandler({BindException.class})
    public ServiceResponse<?> handleBindException(BindException e)
    {
        log.warn("Request binding failed.", e);
        return ServiceResponse.buildErrorResponse(ERROR_BAD_REQUEST, getBindingErrorMessage(e));
    }

    /** Converts common malformed HTTP parameters and payloads into a client-safe error.
     * @param e malformed request exception
     * @return bad-request service response
     */
    @ExceptionHandler({
        ConstraintViolationException.class,
        IllegalArgumentException.class,
        MissingServletRequestParameterException.class,
        MethodArgumentTypeMismatchException.class,
        HttpMessageNotReadableException.class
    })
    public ServiceResponse<?> handleBadRequestException(Exception e)
    {
        log.warn("Bad request.", e);
        return ServiceResponse.buildErrorResponse(ERROR_BAD_REQUEST, e.getMessage());
    }

    /** Converts an unhandled failure into a generic client-safe response after logging it.
     * @param e unexpected server-side exception
     * @return generic internal-error response
     */
    @ExceptionHandler({Exception.class})
    public ServiceResponse<?> handleException(Exception e)
    {
        log.error("Unhandled exception.", e);
        return ServiceResponse.buildErrorResponse(ERROR_INTERNAL, "Internal server error.");
    }

    /** Joins rejected field messages while retaining the binding exception fallback message.
     * @param e binding failure containing field errors
     * @return semicolon-separated validation message
     */
    private static String getBindingErrorMessage(BindException e)
    {
        String message = e.getBindingResult()
            .getFieldErrors()
            .stream()
            .map(GlobalExceptionHandler::toFieldErrorMessage)
            .collect(Collectors.joining("; "));

        return message.isBlank() ? e.getMessage() : message;
    }

    /** Formats one rejected field and its default validation message.
     * @param fieldError rejected field metadata
     * @return field name followed by its validation message
     */
    private static String toFieldErrorMessage(FieldError fieldError)
    {
        return fieldError.getField() + ": " + fieldError.getDefaultMessage();
    }
}
