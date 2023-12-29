package com.cqupt.nginxloggen;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication(scanBasePackages="com.cqupt.controller")

@ComponentScan("com.cqupt")
public class NginxloggenApplication {

    public static void main(String[] args)  {

        SpringApplication.run(NginxloggenApplication.class, args);

    }

}
