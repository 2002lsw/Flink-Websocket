package com.cqupt.controller;


import com.cqupt.service.nginxService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/nginxController")

public class nginxController {

    @Autowired
    nginxService nginxservice;

    @PostMapping("/loggen")
    public void log_gen(
            @RequestParam(name = "filename", required = true) String file_name,
            @RequestParam(name = "eventcount", required = true) int event_count,
            @RequestParam(name = "postman", required = true) String postman){
        nginxservice.gen_log_nginx(file_name,event_count,postman);


    }



}
