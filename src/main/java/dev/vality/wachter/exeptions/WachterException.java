package dev.vality.wachter.exeptions;

public class WachterException extends RuntimeException {

    public WachterException(String message) {
        super(message);
    }

    public WachterException(String message, Throwable cause) {
        super(message, cause);
    }
}
