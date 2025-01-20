package dev.vality.wachter.config;

import dev.vality.wachter.WachterApplication;
import dev.vality.wachter.auth.utils.KeycloakOpenIdStub;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = {WachterApplication.class},
        properties = {
                "wiremock.server.baseUrl=http://localhost:${wiremock.server.port}",
                "spring.security.oauth2.resourceserver.jwt.issuer-uri=${wiremock.server.baseUrl}/auth/realms/" +
                        "${spring.security.oauth2.resourceserver.jwt.realm}"})
@AutoConfigureMockMvc
@AutoConfigureWireMock(port = 0)
@ExtendWith(SpringExtension.class)
public abstract class AbstractKeycloakOpenIdAsWiremockConfig {

    @Autowired
    private KeycloakOpenIdStub keycloakOpenIdStub;

    @BeforeAll
    public static void setUp(@Autowired KeycloakOpenIdStub keycloakOpenIdStub) throws Exception {
        keycloakOpenIdStub.givenStub();
    }

    protected String generateSimpleJwtWithRoles() {
        return keycloakOpenIdStub.generateJwt("Deanonimus", "unknown", "Domain", "messages:methodName",
                "DominantCache", "!DominantCache:methodName");

    }

    protected String generateSimpleJwtWithoutRoles() {
        var token = keycloakOpenIdStub.generateJwt();
        System.out.println(token);
        return token;
    }
}
