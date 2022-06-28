package dev.vality.wachter.exeptions;

public class OrgManagerException extends WachterException {

    public OrgManagerException(String s) {
        super(s);
    }

    public OrgManagerException(String message, Throwable cause) {
        super(message, cause);
    }
}
