package dev.vality.wachter.exeptions;

public class BouncerException extends WachterException {

    public BouncerException(String s) {
        super(s);
    }

    public BouncerException(String message, Throwable cause) {
        super(message, cause);
    }
}
