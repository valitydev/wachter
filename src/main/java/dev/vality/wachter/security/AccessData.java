package dev.vality.wachter.security;

import dev.vality.wachter.config.properties.WachterProperties;
import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class AccessData {

    private final String operationId;
    private final String partyId;
    private final long tokenExpirationSec;
    private final String tokenId;
    private final String userId;
    private final String userEmail;
    private final WachterProperties.Services service;

}
