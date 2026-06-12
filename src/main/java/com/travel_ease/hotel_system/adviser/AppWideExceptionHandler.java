package com.travel_ease.hotel_system.adviser;

import com.travel_ease.hotel_system.exception.*;
import com.travel_ease.hotel_system.util.StandardErrorResponseDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class AppWideExceptionHandler {

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<StandardErrorResponseDto> handleEntryNotFoundException(BadRequestException ex) {
        return new ResponseEntity<StandardErrorResponseDto>(
                new StandardErrorResponseDto(404,ex.getMessage()),
                HttpStatus.NOT_FOUND
        );
    }

    @ExceptionHandler(DuplicateEntryException.class)
    public ResponseEntity<StandardErrorResponseDto> handleAlreadyExistsException(DuplicateEntryException ex) {
        return new ResponseEntity<StandardErrorResponseDto>(
                new StandardErrorResponseDto(409,ex.getMessage()),
                HttpStatus.CONFLICT
        );
    }

    @ExceptionHandler(EntryNotFoundException.class)
    public ResponseEntity<StandardErrorResponseDto> handleAlreadyExistsException(EntryNotFoundException ex) {
        return new ResponseEntity<StandardErrorResponseDto>(
                new StandardErrorResponseDto(409,ex.getMessage()),
                HttpStatus.CONFLICT
        );
    }

    @ExceptionHandler(InternalServerError.class)
    public ResponseEntity<StandardErrorResponseDto> handleAlreadyExistsException(InternalServerError ex) {
        return new ResponseEntity<StandardErrorResponseDto>(
                new StandardErrorResponseDto(500,ex.getMessage()),
                HttpStatus.INTERNAL_SERVER_ERROR
        );
    }

    @ExceptionHandler(KeycloakException.class)
    public ResponseEntity<StandardErrorResponseDto> handleAlreadyExistsException(KeycloakException ex) {
        return new ResponseEntity<StandardErrorResponseDto>(
                new StandardErrorResponseDto(400,ex.getMessage()),
                HttpStatus.BAD_REQUEST
        );
    }

    @ExceptionHandler(UnAuthorizedException.class)
    public ResponseEntity<StandardErrorResponseDto> handleAlreadyExistsException(UnAuthorizedException ex) {
        return new ResponseEntity<StandardErrorResponseDto>(
                new StandardErrorResponseDto(401,ex.getMessage()),
                HttpStatus.UNAUTHORIZED
        );
    }

}
