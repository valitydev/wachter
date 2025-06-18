package dev.vality.wachter.controller;

import dev.vality.wachter.exceptions.AuthorizationException;
import dev.vality.wachter.exceptions.WachterException;
import javassist.NotFoundException;
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

    @ExceptionHandler({WachterException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Object handleBadRequestException(WachterException e) {
        log.warn("<- Res [400]: Not valid request", e);
        return e.getMessage();
    }


    @ExceptionHandler({AccessDeniedException.class})
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public void handleAccessDeniedException(AccessDeniedException e) {
        log.warn("<- Res [403]: Request denied access", e);
    }

    @ExceptionHandler({NotFoundException.class})
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public void handleAccessDeniedException(AuthorizationException e) {
        log.warn("<- Res [404]: Not found", e);
    }

    @ExceptionHandler(HttpClientErrorException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public void handleHttpClientErrorException(HttpClientErrorException e) {
        log.error("<- Res [500]: Error with using inner http client, code={}, body={}",
                e.getStatusCode(), e.getResponseBodyAsString(), e);
    }

    @ExceptionHandler(HttpTimeoutException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public void handleHttpTimeoutException(HttpTimeoutException e) {
        log.error("<- Res [500]: Timeout with using inner http client", e);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public void handleException(Exception e) {
        log.error("<- Res [500]: Unrecognized inner error", e);
    }

}
