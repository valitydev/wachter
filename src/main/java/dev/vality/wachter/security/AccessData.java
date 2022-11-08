package dev.vality.wachter.security;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Builder
@Data
public class AccessData {

    private final String methodName;
    private final String userEmail;
    private final List<String> tokenRoles;
    private final String serviceName;
    private final String traceId;

}
