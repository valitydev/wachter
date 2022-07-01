package dev.vality.wachter.controller;

import dev.vality.wachter.service.WachterService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;


@RestController
@RequiredArgsConstructor
@RequestMapping("")
public class WachterController {

    private final WachterService wachterService;

    @PostMapping("/wachter")
    public byte[] getRequest(HttpServletRequest request) {
        return wachterService.process(request);
    }

}
