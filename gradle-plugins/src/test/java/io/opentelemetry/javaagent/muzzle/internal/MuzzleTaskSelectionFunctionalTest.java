/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.muzzle.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MuzzleTaskSelectionFunctionalTest {
  @TempDir Path directory;

  @Test
  void selectsPartitionsAfterParentPluginCallbacks() throws IOException {
    Files.writeString(
        directory.resolve("settings.gradle"),
        """
        rootProject.name = 'muzzle-selection'
        include ':instrumentation:first', ':instrumentation:second'
        """);
    Files.writeString(
        directory.resolve("build.gradle"),
        """
        import io.opentelemetry.javaagent.muzzle.internal.MuzzleTaskSelection

        plugins {
          id 'io.opentelemetry.instrumentation.muzzle-check' apply false
        }

        project(':instrumentation') {
          def partitions = [tasks.register('muzzle1'), tasks.register('muzzle2')]
          int counter = 0
          subprojects { child ->
            child.plugins.withId('io.opentelemetry.instrumentation.muzzle-check') {
              def aggregate = partitions[counter++].get()
              child.extensions.extraProperties.set(
                MuzzleTaskSelection.AGGREGATE_TASK_PATH_PROPERTY, aggregate.path)
              aggregate.dependsOn(child.tasks.named('muzzle'))
            }
          }
        }
        """);
    for (String name : new String[] {"first", "second"}) {
      Path child = Files.createDirectories(directory.resolve("instrumentation").resolve(name));
      Files.writeString(
          child.resolve("build.gradle"),
          """
          plugins {
            id 'io.opentelemetry.instrumentation.muzzle-check'
          }
          muzzle {
            pass {
              coreJdk.set(true)
            }
          }
          afterEvaluate {
            def assertions = tasks.names.count { it.startsWith('muzzle-Assert') }
            println("Muzzle assertions ${project.path}: ${assertions}")
          }
          """);
    }

    String output =
        GradleRunner.create()
            .withProjectDir(directory.toFile())
            .withPluginClasspath()
            .withArguments(":instrumentation:muzzle1", "--dry-run", "--configuration-cache")
            .build()
            .getOutput();

    assertThat(output)
        .contains(
            "Muzzle assertions :instrumentation:first: 1",
            "Muzzle assertions :instrumentation:second: 0",
            "Configuration cache entry stored");

    output =
        GradleRunner.create()
            .withProjectDir(directory.toFile())
            .withPluginClasspath()
            .withArguments(
                ":instrumentation:muzzle1",
                ":instrumentation:second:muzzle",
                "--dry-run",
                "--configuration-cache")
            .build()
            .getOutput();

    assertThat(output)
        .contains(
            "Muzzle assertions :instrumentation:first: 1",
            "Muzzle assertions :instrumentation:second: 1",
            "Configuration cache entry stored");
  }
}
