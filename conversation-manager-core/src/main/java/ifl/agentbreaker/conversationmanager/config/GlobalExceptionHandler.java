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

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler
{
    private static final int ERROR_BAD_REQUEST = -100;
    private static final int ERROR_INTERNAL = -200;
    private static final int ERROR_NOT_LOGGED_IN = 2001;

    @ExceptionHandler({NotLoggedInException.class})
    public ServiceResponse<?> handleNotLoggedInException(NotLoggedInException e)
    {
        log.warn("Request is not logged in.");
        return ServiceResponse.buildErrorResponse(ERROR_NOT_LOGGED_IN, e.getMessage());
    }

    @ExceptionHandler({ServiceResponseException.class})
    public ServiceResponse<?> handleServiceResponseException(ServiceResponseException e)
    {
        log.warn("Business request failed: {}", e.getMessage());
        return ServiceResponse.buildErrorResponse(e.getCode(), e.getMessage());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class})
    public ServiceResponse<?> handleMethodArgumentNotValidException(MethodArgumentNotValidException e)
    {
        log.warn("Request validation failed.", e);
        return ServiceResponse.buildErrorResponse(ERROR_BAD_REQUEST, getBindingErrorMessage(e));
    }

    @ExceptionHandler({BindException.class})
    public ServiceResponse<?> handleBindException(BindException e)
    {
        log.warn("Request binding failed.", e);
        return ServiceResponse.buildErrorResponse(ERROR_BAD_REQUEST, getBindingErrorMessage(e));
    }

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

    @ExceptionHandler({Exception.class})
    public ServiceResponse<?> handleException(Exception e)
    {
        log.error("Unhandled exception.", e);
        return ServiceResponse.buildErrorResponse(ERROR_INTERNAL, "Internal server error.");
    }

    private static String getBindingErrorMessage(BindException e)
    {
        String message = e.getBindingResult()
            .getFieldErrors()
            .stream()
            .map(GlobalExceptionHandler::toFieldErrorMessage)
            .collect(Collectors.joining("; "));

        return message.isBlank() ? e.getMessage() : message;
    }

    private static String toFieldErrorMessage(FieldError fieldError)
    {
        return fieldError.getField() + ": " + fieldError.getDefaultMessage();
    }
}
