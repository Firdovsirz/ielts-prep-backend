package com.ieltsprep.settings;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Creates the default settings row on start-up (from ielts.defaults.*) if it does not exist yet. */
@Component
@Order(2)
public class SettingsBootstrap implements ApplicationRunner {

    private final SettingsService settings;

    public SettingsBootstrap(SettingsService settings) {
        this.settings = settings;
    }

    @Override
    public void run(ApplicationArguments args) {
        settings.ensureExists();
    }
}
