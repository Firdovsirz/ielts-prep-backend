package com.ieltsprep.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Background jobs (buffer top-up, batch polling, weekly coach report). Disabled for CLI tasks and tests. */
@Configuration
@EnableScheduling
@EnableAsync
@ConditionalOnProperty(name = "ielts.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {}
