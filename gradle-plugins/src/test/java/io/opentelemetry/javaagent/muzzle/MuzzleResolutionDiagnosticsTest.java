/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.muzzle;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarOutputStream;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MuzzleResolutionDiagnosticsTest {

  private static final String DIAGNOSTICS = "Dependency resolution failures collected for";

  @TempDir Path projectDir;

  @Test
  void failedPassReportsResolutionFailures() throws IOException {
    prepareProject(true, false, true);
    assertCachedBuilds(
        true,
        output ->
            assertThat(output)
                .contains(
                    "Instrumentation failed Muzzle validation",
                    DIAGNOSTICS,
                    "Could not find test:missing:1.0.",
                    "Searched in the following locations:",
                    "Required by:"));
  }

  @Test
  void failedPassReportsResolutionCauses() throws IOException {
    prepareProject(true, false, true);
    Path missingDir = projectDir.resolve("repo/test/missing/1.0");
    Files.createDirectories(missingDir);
    Files.writeString(missingDir.resolve("missing-1.0.pom"), "not a pom");
    assertCachedBuilds(
        true,
        output ->
            assertThat(output)
                .contains(
                    "Instrumentation failed Muzzle validation",
                    DIAGNOSTICS,
                    "Could not parse POM",
                    "SAXParseException",
                    "Caused by:"));
  }

  @Test
  void failedPassWithoutResolutionFailures() throws IOException {
    prepareProject(true, false, false);
    assertCachedBuilds(
        true,
        output ->
            assertThat(output)
                .contains("Instrumentation failed Muzzle validation")
                .doesNotContain(DIAGNOSTICS));
  }

  @Test
  void successfulLenientPassIsQuiet() throws IOException {
    prepareProject(true, true, true);
    assertCachedBuilds(false, output -> assertThat(output).doesNotContain(DIAGNOSTICS));
  }

  @Test
  void expectedFailIsQuiet() throws IOException {
    prepareProject(false, false, true);
    assertCachedBuilds(false, output -> assertThat(output).doesNotContain(DIAGNOSTICS));
  }

  @Test
  void unexpectedPassPreservesAssertFailFailure() throws IOException {
    prepareProject(false, true, true);
    assertCachedBuilds(
        true,
        output ->
            assertThat(output)
                .contains("Instrumentation unexpectedly passed Muzzle validation")
                .doesNotContain(DIAGNOSTICS));
  }

  private void assertCachedBuilds(boolean fails, java.util.function.Consumer<String> assertions) {
    GradleRunner runner =
        GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments("muzzle", "--configuration-cache");
    BuildResult first = fails ? runner.buildAndFail() : runner.build();
    assertions.accept(first.getOutput());
    assertThat(first.getOutput()).contains("Configuration cache entry stored");
    BuildResult second = fails ? runner.buildAndFail() : runner.build();
    assertions.accept(second.getOutput());
    assertThat(second.getOutput()).contains("Reusing configuration cache");
  }

  private void prepareProject(boolean assertPass, boolean matches, boolean brokenDependency)
      throws IOException {
    Files.writeString(projectDir.resolve("settings.gradle"), "rootProject.name = 'muzzle-test'\n");
    Files.writeString(
        projectDir.resolve("build.gradle"),
        """
        plugins {
          id 'io.opentelemetry.instrumentation.muzzle-check'
        }
        repositories {
          maven { url = uri('repo') }
        }
        muzzle {
          %s {
            group.set('test')
            module.set('library')
            versions.set('1.0')
          }
        }
        """
            .formatted(assertPass ? "pass" : "fail"));

    Path artifactDir = projectDir.resolve("repo/test/library/1.0");
    Files.createDirectories(artifactDir);
    Files.writeString(
        artifactDir.resolve("library-1.0.pom"),
        """
        <project>
          <modelVersion>4.0.0</modelVersion>
          <groupId>test</groupId>
          <artifactId>library</artifactId>
          <version>1.0</version>
          %s
        </project>
        """
            .formatted(
                brokenDependency
                    ? """
                      <dependencies>
                        <dependency>
                          <groupId>test</groupId>
                          <artifactId>missing</artifactId>
                          <version>1.0</version>
                        </dependency>
                      </dependencies>
                      """
                    : ""));
    try (JarOutputStream jar =
        new JarOutputStream(Files.newOutputStream(artifactDir.resolve("library-1.0.jar")))) {
      // A resource lets the matcher fixture verify the resolved user classpath.
      if (matches) {
        jar.putNextEntry(new java.util.jar.JarEntry("muzzle-match"));
        jar.closeEntry();
      }
    }

    // Exercise the plugin's reflective validation boundary without depending on the agent build.
    Path matcher =
        projectDir.resolve(
            "src/main/java/io/opentelemetry/javaagent/tooling/muzzle/ClassLoaderMatcher.java");
    Files.createDirectories(matcher.getParent());
    Files.writeString(
        matcher,
        """
        package io.opentelemetry.javaagent.tooling.muzzle;

        import java.util.Collections;
        import java.util.List;
        import java.util.Map;
        import java.util.Set;

        public class ClassLoaderMatcher {
          public static Map<String, List<String>> matchesAll(
              ClassLoader loader, boolean assertPass, Set<String> names, Set<String> modules) {
            return Collections.singletonMap("test-module",
                loader.getResource("muzzle-match") != null
                    ? Collections.emptyList() : Collections.singletonList("missing library symbol"));
          }
        }
        """);
  }
}
