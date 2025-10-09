package dev.vality.wachter.controller;

import dev.vality.wachter.exceptions.AuthorizationException;
import dev.vality.wachter.exceptions.NotFoundException;
import dev.vality.wachter.exceptions.WachterException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.HttpClientErrorException;

import java.net.http.HttpTimeoutException;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class ErrorControllerAdvice {

    @ExceptionHandler({AuthorizationException.class})
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public void handleAuthorizationException(AuthorizationException e) {
        log.warn("<- Res [401]: Request denied access", e);
    }

    @ExceptionHandler({NotFoundException.class})
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public void handleNotFoundException(NotFoundException e) {
        log.warn("<- Res [404]: Not found", e);
    }

    @ExceptionHandler({WachterException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public void handleBadRequestException(WachterException e) {
        log.warn("<- Res [400]: Not valid request", e);
    }

    @ExceptionHandler({AccessDeniedException.class})
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public void handleAccessDeniedException(AccessDeniedException e) {
        log.warn("<- Res [403]: Request denied access", e);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public void handleException(Throwable e) {
        log.error("<- Res [500]: Unrecognized inner error", e);
    }
}
