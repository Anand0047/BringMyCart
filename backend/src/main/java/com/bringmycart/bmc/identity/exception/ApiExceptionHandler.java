package com.bringmycart.bmc.identity.exception;

import java.time.Instant;
import java.util.Map;

import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiError> apiException(ApiException exception) {
        return ResponseEntity.status(exception.getStatus()).body(ApiError.of(exception.getStatus(), exception.getCode(), exception.getMessage(), Map.of()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validation(MethodArgumentNotValidException exception) {
        Map<String, String> fields = exception.getBindingResult().getFieldErrors().stream()
            .collect(java.util.stream.Collectors.toMap(FieldError::getField, FieldError::getDefaultMessage, (left, right) -> left));
        return ResponseEntity.badRequest().body(ApiError.of(HttpStatus.BAD_REQUEST, "validation_failed", "Request validation failed", fields));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiError> constraintViolation() {
        return ResponseEntity.badRequest().body(ApiError.of(HttpStatus.BAD_REQUEST, "validation_failed", "Request validation failed", Map.of()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> dataIntegrityViolation() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ApiError.of(HttpStatus.CONFLICT, "conflicting_request", "The request conflicts with existing data", Map.of()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiError> resourceNotFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ApiError.of(HttpStatus.NOT_FOUND, "not_found", "The requested resource was not found", Map.of()));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpectedException() {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiError.of(HttpStatus.INTERNAL_SERVER_ERROR, "internal_error", "An unexpected error occurred", Map.of()));
    }

    public record ApiError(Instant timestamp, int status, String code, String message, Map<String, String> fieldErrors) {
        static ApiError of(HttpStatus status, String code, String message, Map<String, String> fieldErrors) {
            return new ApiError(Instant.now(), status.value(), code, message, fieldErrors);
        }
    }
}
