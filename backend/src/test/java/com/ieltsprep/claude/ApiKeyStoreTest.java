package com.ieltsprep.claude;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ApiKeyStoreTest {

    @Test
    void aKeySavedInTheAppOverridesTheEnvironmentAndSurvivesARestart(@TempDir Path dir) throws Exception {
        Path file = dir.resolve(".anthropic-api-key");
        ApiKeyStore store = new ApiKeyStore("sk-ant-from-env-0000", file);
        assertThat(store.source()).isEqualTo(ApiKeyStore.Source.ENV);
        assertThat(store.hint()).isEqualTo("…0000");
        long v = store.version();

        store.save("  sk-ant-saved-in-app-1234 \n");
        assertThat(store.current()).contains("sk-ant-saved-in-app-1234");
        assertThat(store.source()).isEqualTo(ApiKeyStore.Source.APP);
        assertThat(store.version()).isGreaterThan(v);
        assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(file))).isEqualTo("rw-------");

        ApiKeyStore restarted = new ApiKeyStore("sk-ant-from-env-0000", file);
        assertThat(restarted.current()).contains("sk-ant-saved-in-app-1234");

        restarted.clear();
        assertThat(Files.exists(file)).isFalse();
        assertThat(restarted.source()).isEqualTo(ApiKeyStore.Source.ENV);
        assertThat(restarted.current()).contains("sk-ant-from-env-0000");
    }

    @Test
    void withoutAnyKeyNothingIsConfigured(@TempDir Path dir) {
        ApiKeyStore store = new ApiKeyStore("", dir.resolve("key"));
        assertThat(store.present()).isFalse();
        assertThat(store.source()).isEqualTo(ApiKeyStore.Source.NONE);
        assertThat(store.hint()).isNull();
    }
}
