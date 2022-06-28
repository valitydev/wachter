package dev.vality.wachter.exeptions;

public class DeadlineException extends WachterException {

    public DeadlineException(String message) {
        super(message);
    }

    public DeadlineException(String message, Throwable cause) {
        super(message, cause);
    }
}
