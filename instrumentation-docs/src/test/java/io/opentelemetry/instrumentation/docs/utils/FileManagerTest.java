/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs.utils;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.docs.internal.InstrumentationType;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
    fileManager = new FileManager(tempDir);
  }

  @Test
  void testGetInstrumentationPaths() throws IOException {
    Files.createDirectories(tempDir.resolve("instrumentation/my-instrumentation/javaagent"));
    List<InstrumentationPath> paths = fileManager.getInstrumentationPaths();
    assertThat(paths).hasSize(1);
    assertThat(paths.get(0).instrumentationName()).isEqualTo("my-instrumentation");
    assertThat(paths.get(0).type()).isEqualTo(InstrumentationType.JAVAAGENT);
    // src paths are relative to the repository root and always use forward slashes, even on Windows
    assertThat(paths.get(0).srcPath()).isEqualTo("instrumentation/my-instrumentation");
  }

  @Test
  void testGetInstrumentationPathsWithTrailingSeparatorRootDir() throws IOException {
    Files.createDirectories(tempDir.resolve("instrumentation/my-instrumentation/library"));
    // a root dir with a trailing separator, as produced by appending "/" to the basePath property
    FileManager trailingSeparator = new FileManager(Paths.get(tempDir + "/"));

    List<InstrumentationPath> paths = trailingSeparator.getInstrumentationPaths();

    assertThat(paths).hasSize(1);
    assertThat(paths.get(0).srcPath()).isEqualTo("instrumentation/my-instrumentation");
  }

  @Test
  void testGetInstrumentationPathsWithExcludedWordInRootDir() throws IOException {
    // the checkout directory contains "build", which the exclusion rules match on
    Path rootDir = tempDir.resolve("build/repo");
    Files.createDirectories(rootDir.resolve("instrumentation/my-instrumentation/javaagent"));

    List<InstrumentationPath> paths = new FileManager(rootDir).getInstrumentationPaths();

    assertThat(paths).hasSize(1);
    assertThat(paths.get(0).srcPath()).isEqualTo("instrumentation/my-instrumentation");
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

    List<Path> gradleFiles = fileManager.findBuildGradleFiles("instrumentation/runtime-telemetry");

    assertThat(gradleFiles)
        .containsExactlyInAnyOrder(
            javaagent.resolve("build.gradle.kts"), library.resolve("build.gradle.kts"));
    assertThat(gradleFiles)
        .doesNotContain(
            nestedJava17.resolve("build.gradle.kts"), nestedJava8.resolve("build.gradle.kts"));
  }
}
