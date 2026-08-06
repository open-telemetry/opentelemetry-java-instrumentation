/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs.utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@SuppressWarnings("NullAway")
class FileManagerTest {

  @TempDir Path tempDir;

  private FileManager fileManager;

  @BeforeEach
  void setUp() {
    fileManager = new FileManager(tempDir.toString() + "/");
  }

  @Test
  void testGetInstrumentationPaths() throws IOException {
    Path validDir =
        Files.createDirectories(tempDir.resolve("instrumentation/my-instrumentation/javaagent"));
    List<InstrumentationPath> paths = fileManager.getInstrumentationPaths();
    assertThat(paths).hasSize(1);
    // paths always use forward slashes, even on Windows, so that they can be made relative to the
    // repository root via string manipulation
    assertThat(paths.get(0).srcPath())
        .isEqualTo(validDir.toString().replace('\\', '/'))
        .doesNotContain("\\");
  }

  @Test
  void testGetMetaDataFileWithMixedSeparatorRootDir() throws IOException {
    // on Windows tempDir uses backslashes while the module path uses forward slashes
    Path moduleDir = Files.createDirectories(tempDir.resolve("instrumentation/my-instrumentation"));
    Files.writeString(moduleDir.resolve("metadata.yaml"), "description: test\n");

    assertThat(fileManager.getMetaDataFile("instrumentation/my-instrumentation"))
        .isEqualTo("description: test\n");
  }

  @Test
  void testGetMetaDataFileReturnsNullWhenMissing() {
    assertThat(fileManager.getMetaDataFile("instrumentation/my-instrumentation")).isNull();
  }

  @Test
  void testIsValidInstrumentationPath() {
    assertThat(
            FileManager.isValidInstrumentationPath("/instrumentation/my-instrumentation/javaagent"))
        .isTrue();
    assertThat(FileManager.isValidInstrumentationPath("invalid/test/javaagent")).isFalse();
    assertThat(FileManager.isValidInstrumentationPath("/instrumentation/test/javaagent")).isFalse();
  }

  @Test
  void testExcludesCommonModules() {
    assertThat(
            FileManager.isValidInstrumentationPath(
                "instrumentation/elasticsearch/elasticsearch-rest-common-5.0"))
        .isFalse();
  }

  @Test
  void testFindBuildGradleFilesExcludesNestedModules() throws IOException {
    // mimicking runtime-telemetry with nested instrumentation modules
    Path runtimeTelemetry = tempDir.resolve("instrumentation/runtime-telemetry");
    Path javaagent = Files.createDirectories(runtimeTelemetry.resolve("javaagent"));
    Path library = Files.createDirectories(runtimeTelemetry.resolve("library"));
    Path nestedJava17 =
        Files.createDirectories(runtimeTelemetry.resolve("runtime-telemetry-java17/javaagent"));
    Path nestedJava8 =
        Files.createDirectories(runtimeTelemetry.resolve("runtime-telemetry-java8/library"));

    Files.createFile(javaagent.resolve("build.gradle.kts"));
    Files.createFile(library.resolve("build.gradle.kts"));
    Files.createFile(nestedJava17.resolve("build.gradle.kts"));
    Files.createFile(nestedJava8.resolve("build.gradle.kts"));

    List<String> gradleFiles =
        fileManager.findBuildGradleFiles("instrumentation/runtime-telemetry");

    // paths always use forward slashes, even on Windows, so that downstream consumers can match on
    // "/javaagent/" and "/library/" segments
    assertThat(gradleFiles)
        .containsExactlyInAnyOrder(
            normalized(javaagent.resolve("build.gradle.kts")),
            normalized(library.resolve("build.gradle.kts")));
    assertThat(gradleFiles)
        .doesNotContain(
            normalized(nestedJava17.resolve("build.gradle.kts")),
            normalized(nestedJava8.resolve("build.gradle.kts")));
  }

  private static String normalized(Path path) {
    return path.toString().replace('\\', '/');
  }
}
