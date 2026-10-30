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

class MuzzleClassLoaderFunctionalTest {
  @TempDir Path directory;

  @Test
  void reusesInstrumentationClassesWithFreshUserLoadersAndBuildScopedState() throws IOException {
    Files.writeString(
        directory.resolve("settings.gradle"), "rootProject.name = 'muzzle-classloaders'\n");
    Files.writeString(
        directory.resolve("build.gradle"),
        """
        plugins {
          id 'io.opentelemetry.instrumentation.muzzle-check'
        }
        muzzle {
          pass {
            coreJdk.set(true)
          }
          fail {
            coreJdk.set(true)
            if (providers.gradleProperty('failAssertion').isPresent()) {
              excludeInstrumentationName('fail')
            }
          }
        }
        """);
    Path sources =
        Files.createDirectories(
            directory.resolve(
                Path.of(
                    "src",
                    "main",
                    "java",
                    "io",
                    "opentelemetry",
                    "javaagent",
                    "tooling",
                    "muzzle")));
    Files.writeString(
        sources.resolve("ClassLoaderMatcher.java"),
        """
        package io.opentelemetry.javaagent.tooling.muzzle;

        import java.util.List;
        import java.util.Map;
        import java.util.Set;

        public class ClassLoaderMatcher {
          private static ClassLoader previous;
          private static int calls;

          public static Map<String, List<Object>> matchesAll(
              ClassLoader loader, boolean injectHelpers, Set<String> names, Set<String> modules) {
            if (loader == previous) {
              throw new IllegalStateException("User class loader was reused");
            }
            previous = loader;
            System.out.println("Instrumentation loader invocation " + ++calls);
            if (names.contains("fail")) {
              throw new IllegalStateException("Deliberate assertion failure");
            }
            List<Object> mismatches = injectHelpers ? List.of() : List.of("expected rejection");
            return Map.of("example", mismatches);
          }
        }
        """);

    for (int build = 0; build < 2; build++) {
      BuildResult result =
          GradleRunner.create()
              .withProjectDir(directory.toFile())
              .withPluginClasspath()
              .withArguments("muzzle", "--configuration-cache")
              .build();

      assertThat(
              result
                  .getOutput()
                  .lines()
                  .filter(line -> line.startsWith("Instrumentation loader invocation "))
                  .toList())
          .containsExactly(
              "Instrumentation loader invocation 1", "Instrumentation loader invocation 2");
      assertThat(result.task(":closeMuzzleClassLoader").getOutcome())
          .isEqualTo(TaskOutcome.SUCCESS);
      assertThat(result.getOutput())
          .contains(
              build == 0 ? "Configuration cache entry stored" : "Reusing configuration cache");
    }

    BuildResult failure =
        GradleRunner.create()
            .withProjectDir(directory.toFile())
            .withPluginClasspath()
            .withArguments(
                "muzzle", "--configuration-cache", "-PfailAssertion=true", "--stacktrace")
            .buildAndFail();

    assertThat(failure.getOutput()).contains("Deliberate assertion failure");
    assertThat(failure.task(":closeMuzzleClassLoader").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
  }
}
