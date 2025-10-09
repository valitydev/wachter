package dev.vality.wachter.config;

import dev.vality.wachter.WachterApplication;
import dev.vality.wachter.auth.utils.KeycloakOpenIdStub;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.wiremock.spring.EnableWireMock;

import java.security.PrivateKey;

@SuppressWarnings("LineLength")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
        classes = {WachterApplication.class},
        properties = {
                "server.port=8083",
                "spring.security.oauth2.resourceserver.url=${wiremock.server.baseUrl}",
                "spring.security.oauth2.resourceserver.jwt.issuer-uri=${wiremock.server.baseUrl}/auth/realms/" +
                        "${spring.security.oauth2.resourceserver.jwt.realm}"})
@AutoConfigureMockMvc
@EnableWireMock
@ExtendWith(SpringExtension.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class AbstractKeycloakOpenIdAsWiremockConfig {

    @Autowired
    private KeycloakOpenIdStub keycloakOpenIdStub;

    @BeforeEach
    public void setUp(@Autowired KeycloakOpenIdStub keycloakOpenIdStub) throws Exception {
        keycloakOpenIdStub.givenStub();
    }

    protected String generateSimpleJwtWithRoles() {
        return keycloakOpenIdStub.generateJwt("Deanonimus", "unknown", "Domain", "messages:methodName",
                "DominantCache", "!DominantCache:methodName", "MerchantStatistics", "PaymentAdjustment");

    }

    protected String generateSimpleJwtWithRolesAndCustomKey(PrivateKey privateKey) {
        return keycloakOpenIdStub.generateJwtWithCustomKey(privateKey, "Deanonimus", "unknown", "Domain",
                "messages:methodName",
                "DominantCache", "!DominantCache:methodName");

    }

    protected String generateSimpleJwtWithoutRoles() {
        return keycloakOpenIdStub.generateJwt();
    }
}
