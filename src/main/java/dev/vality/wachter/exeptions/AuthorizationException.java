package dev.vality.wachter.exeptions;

public class AuthorizationException extends WachterException {

    public AuthorizationException(String s) {
        super(s);
    }

    public AuthorizationException(String message, Throwable cause) {
        super(message, cause);
    }
}
