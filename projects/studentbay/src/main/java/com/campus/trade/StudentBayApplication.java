package com.campus.trade;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class StudentBayApplication {

    private static final Logger log = LoggerFactory.getLogger(StudentBayApplication.class);

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(StudentBayApplication.class);
        app.addListeners((ApplicationListener<ApplicationReadyEvent>) event -> {
            Environment env = event.getApplicationContext().getEnvironment();
            String port = env.getProperty("server.port", "8080");
            String url = "http://localhost:" + port;
            log.info("======================================================");
            log.info("StudentBay 已启动，请点击访问: {}", url);
            log.info("======================================================");
        });
        app.run(args);
    }

}
