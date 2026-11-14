package com.ilich.paymentengine.controller;

import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@Hidden
public class HomeController {

    /**
     * Forwards the root path '/' to the interactive HTML Dashboard.
     */
    @GetMapping("/")
    public String index() {
        return "forward:/index.html";
    }
}
