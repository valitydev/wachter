package dev.vality.wachter.controller;

import dev.vality.wachter.service.WachterService;
import lombok.RequiredArgsConstructor;
import org.apache.thrift.TException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;


@RestController
@RequiredArgsConstructor
@RequestMapping("/${server.endpoint}")
public class WachterController {

    private final WachterService wachterService;

    @PostMapping("/request")
    public byte[] getRequest(HttpServletRequest request) throws IOException, TException {
        return wachterService.process(request);
    }

}
