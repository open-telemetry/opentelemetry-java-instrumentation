/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.muzzle;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MuzzleTaskSelectionTest {

  @TempDir Path directory;

  @Test
  void partitionRequestSchedulesModuleAssertion() throws IOException {
    write("settings.gradle", "rootProject.name = 'muzzle-selection'\ninclude 'instrumentation:alpha'\n");
    // Exercise the real aggregate script, substituting only its unrelated convention plugin.
    write(
        "instrumentation/build.gradle.kts",
        Files.readString(Path.of(System.getProperty("muzzle.aggregateBuildScript")))
            .replace("id(\"otel.java-conventions\")", "java"));
    write(
        "instrumentation/alpha/build.gradle",
        """
        plugins { id 'io.opentelemetry.instrumentation.muzzle-check' }
        muzzle {
          pass {
            coreJdk.set(true)
          }
        }
        """);

    BuildResult result =
        GradleRunner.create()
            .withProjectDir(directory.toFile())
            .withPluginClasspath()
            .withArguments("instrumentation:muzzle1", "--dry-run", "--no-configuration-cache")
            .build();

    assertThat(result.getOutput())
        .contains(":instrumentation:alpha:muzzle-AssertPass-core-jdk SKIPPED");
  }

  private void write(String path, String content) throws IOException {
    Path file = directory.resolve(path);
    Files.createDirectories(file.getParent());
    Files.writeString(file, content);
  }
}
