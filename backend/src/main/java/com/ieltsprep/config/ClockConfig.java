package com.ieltsprep.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {

    /** System clock in the local zone (spend cap days, study-plan dates). Tests substitute a fixed clock. */
    @Bean
    Clock clock() {
        return Clock.systemDefaultZone();
    }
}
