package dev.vality.wachter.client;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;

public record WachterClientResponse(HttpStatusCode statusCode, HttpHeaders headers, byte[] body) {
}
