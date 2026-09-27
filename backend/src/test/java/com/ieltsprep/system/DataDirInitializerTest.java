package com.ieltsprep.system;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DataDirInitializerTest {

    @Test
    void copiesBundledDefaultsIntoAnEmptyVolumeWithoutOverwriting(@TempDir Path tmp) throws Exception {
        Path defaults = tmp.resolve("defaults");
        Files.createDirectories(defaults.resolve("descriptors"));
        Files.writeString(defaults.resolve("descriptors/grammar-areas.json"), "{\"bundled\":true}");
        Files.writeString(defaults.resolve("descriptors/awl.json"), "{}");
        Path data = tmp.resolve("data");
        Files.createDirectories(data.resolve("descriptors"));
        Files.writeString(data.resolve("descriptors/awl.json"), "{\"edited\":true}");

        DataDirInitializer.initialise(data, defaults.toString());

        assertThat(data.resolve("db")).isDirectory();
        assertThat(data.resolve("recordings")).isDirectory();
        assertThat(Files.readString(data.resolve("descriptors/grammar-areas.json"))).contains("bundled");
        assertThat(Files.readString(data.resolve("descriptors/awl.json"))).contains("edited");
    }
}
