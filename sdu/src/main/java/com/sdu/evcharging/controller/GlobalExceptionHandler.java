package com.sdu.evcharging.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.sdu.evcharging.dto.ErrorResponse;
import com.sdu.evcharging.service.auth.InvalidCredentialsException;

import lombok.extern.slf4j.Slf4j;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCredentialsException(InvalidCredentialsException e) {
        log.error("InvalidCredentialsException: {}", e.getMessage());

        ErrorResponse errorResponse = new ErrorResponse(
                e.getMessage(),
                "InvalidCredentialsException",
                HttpStatus.UNAUTHORIZED.value()
        );

        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(errorResponse);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(AccessDeniedException e) {
        log.error("AccessDeniedException: {}", e.getMessage());

        ErrorResponse errorResponse = new ErrorResponse(
                "You do not have permission to access this resource",
                "AccessDeniedException",
                HttpStatus.FORBIDDEN.value()
        );

        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(errorResponse);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalStateException(IllegalStateException e) {
        log.error("IllegalStateException: {}", e.getMessage());
        
        ErrorResponse errorResponse = new ErrorResponse(
                e.getMessage(),
                "IllegalStateException",
                HttpStatus.BAD_REQUEST.value()
        );
        
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(errorResponse);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException e) {
        log.error("IllegalArgumentException: {}", e.getMessage());
        
        ErrorResponse errorResponse = new ErrorResponse(
                e.getMessage(),
                "IllegalArgumentException",
                HttpStatus.BAD_REQUEST.value()
        );
        
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(errorResponse);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception e) {
        log.error("Unexpected exception: {}", e.getMessage(), e);
        
        ErrorResponse errorResponse = new ErrorResponse(
                "An unexpected error occurred: " + e.getMessage(),
                "Exception",
                HttpStatus.INTERNAL_SERVER_ERROR.value()
        );
        
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(errorResponse);
    }
}
