package com.ieltsprep;

import java.util.Arrays;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class IeltsPrepApplication {

    public static void main(String[] args) {
        boolean cliTask = Arrays.stream(args).anyMatch(a -> a.startsWith("--task="));
        if (cliTask) {
            // Content-pipeline tasks (fetch-templates, fetch-sources, generate, seed) run without the web
            // server or background schedulers and exit with the task's status code.
            var context = new SpringApplicationBuilder(IeltsPrepApplication.class)
                    .web(WebApplicationType.NONE)
                    .properties("ielts.scheduling.enabled=false")
                    .run(args);
            System.exit(SpringApplication.exit(context));
        }
        SpringApplication.run(IeltsPrepApplication.class, args);
    }
}
