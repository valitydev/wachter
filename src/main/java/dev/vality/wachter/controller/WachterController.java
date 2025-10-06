package dev.vality.wachter.controller;

import dev.vality.wachter.service.WachterService;
import dev.vality.wachter.utils.DeadlineUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static dev.vality.wachter.constants.HeadersConstants.X_REQUEST_DEADLINE;
import static dev.vality.wachter.constants.HeadersConstants.X_REQUEST_ID;


@RestController
@RequiredArgsConstructor
@RequestMapping("")
@SuppressWarnings("LocalVariableName")
public class WachterController {

    private final WachterService wachterService;

    @PostMapping("/wachter")
    public ResponseEntity<byte[]> proxyRequest(HttpServletRequest request) {
        var xRequestDeadline = request.getHeader(X_REQUEST_DEADLINE);
        var xRequestId = request.getHeader(X_REQUEST_ID);
        DeadlineUtil.checkDeadline(xRequestDeadline, xRequestId);
        var upstreamResponse = wachterService.process(request);
        var responseHeaders = new HttpHeaders();
        responseHeaders.putAll(upstreamResponse.headers());
        return ResponseEntity.status(upstreamResponse.statusCode())
                .headers(responseHeaders)
                .body(upstreamResponse.body());
    }
}
