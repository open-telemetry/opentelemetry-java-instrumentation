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

class FileManagerTest {

  @TempDir Path tempDir;

  private FileManager fileManager;

  @BeforeEach
  void setUp() {
    fileManager = new FileManager(tempDir.toString());
  }

  @Test
  void testGetInstrumentationPaths() throws IOException {
    Path validDir =
        Files.createDirectories(tempDir.resolve("instrumentation/my-instrumentation/javaagent"));
    List<InstrumentationPath> paths = fileManager.getInstrumentationPaths();
    assertThat(paths).hasSize(1);
    assertThat(paths.get(0).srcPath()).isEqualTo(validDir.toString());
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
  void testFindBuildGradleFiles() throws IOException {
    Path gradleFile = Files.createFile(tempDir.resolve("build.gradle.kts"));
    Path nonGradleFile = Files.createFile(tempDir.resolve("gradle.properties"));
    List<String> gradleFiles = fileManager.findBuildGradleFiles(tempDir.toString());
    assertThat(gradleFiles).contains(gradleFile.toString());
    assertThat(gradleFiles).doesNotContain(nonGradleFile.toString());
  }
}
