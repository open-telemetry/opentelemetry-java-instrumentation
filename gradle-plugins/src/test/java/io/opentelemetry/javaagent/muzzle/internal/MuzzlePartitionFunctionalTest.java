/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.muzzle.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MuzzlePartitionFunctionalTest {
  @TempDir Path directory;

  @Test
  void runsSelectedPartitionAssertionsAndReportsExpectedMismatch() throws IOException {
    Files.writeString(
        directory.resolve("settings.gradle"),
        """
        rootProject.name = 'muzzle-partition'
        include ':instrumentation:compatible', ':instrumentation:incompatible'
        """);
    Files.writeString(
        directory.resolve("build.gradle"),
        """
        import io.opentelemetry.javaagent.muzzle.internal.MuzzleTaskSelection

        plugins {
          id 'io.opentelemetry.instrumentation.muzzle-check' apply false
        }

        project(':instrumentation') {
          def partition = tasks.register('muzzle1')
          subprojects { child ->
            child.plugins.withId('io.opentelemetry.instrumentation.muzzle-check') {
              child.extensions.extraProperties.set(
                MuzzleTaskSelection.AGGREGATE_TASK_PATH_PROPERTY, partition.get().path)
              partition.configure { dependsOn(child.tasks.named('muzzle')) }
            }
          }
        }
        """);

    for (String fixture : new String[] {"compatible", "incompatible"}) {
      Path project = Files.createDirectories(directory.resolve("instrumentation").resolve(fixture));
      Files.writeString(
          project.resolve("build.gradle"),
          """
          plugins {
            id 'io.opentelemetry.instrumentation.muzzle-check'
          }
          muzzle {
            pass {
              coreJdk.set(true)
            }
          }
          """);
      Path sources =
          Files.createDirectories(
              project.resolve(
                  "src/main/java/io/opentelemetry/javaagent/tooling/muzzle"));
      Files.writeString(
          sources.resolve("ClassLoaderMatcher.java"),
          """
          package io.opentelemetry.javaagent.tooling.muzzle;

          import java.util.List;
          import java.util.Map;
          import java.util.Set;

          public class ClassLoaderMatcher {
            public static Map<String, List<Object>> matchesAll(
                ClassLoader loader, boolean injectHelpers, Set<String> names, Set<String> modules) {
              System.out.println("MUZZLE_ASSERTION_EXECUTED: %s");
              List<Object> mismatches = %s;
              return Map.of("fixture.%s", mismatches);
            }
          }
          """
              .formatted(
                  fixture,
                  fixture.equals("incompatible")
                      ? "List.of(\"expected mismatch: missing class fixture.MissingType\")"
                      : "List.of()",
                  fixture));
    }

    BuildResult result =
        GradleRunner.create()
            .withProjectDir(directory.toFile())
            .withPluginClasspath()
            .withArguments(":instrumentation:muzzle1")
            .buildAndFail();

    assertThat(result.getOutput())
        .contains(
            "MUZZLE_ASSERTION_EXECUTED: compatible",
            "MUZZLE_ASSERTION_EXECUTED: incompatible",
            "FAILED MUZZLE VALIDATION: fixture.incompatible mismatches:",
            "-- expected mismatch: missing class fixture.MissingType",
            "Instrumentation failed Muzzle validation");
    assertThat(result.task(":instrumentation:compatible:muzzle-AssertPass-core-jdk").getOutcome())
        .isEqualTo(TaskOutcome.SUCCESS);
    assertThat(result.task(":instrumentation:incompatible:muzzle-AssertPass-core-jdk").getOutcome())
        .isEqualTo(TaskOutcome.FAILED);
  }
}
