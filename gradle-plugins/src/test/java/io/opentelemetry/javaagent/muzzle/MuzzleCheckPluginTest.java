/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.muzzle;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import javax.tools.ToolProvider;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MuzzleCheckPluginTest {

  @TempDir Path projectDir;

  private HttpServer repository;
  private Path repositoryDir;

  @BeforeEach
  void setUp() throws IOException {
    repositoryDir = Files.createDirectory(projectDir.resolve("repository"));
    repository = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    repository.createContext(
        "/",
        exchange -> {
          Path file =
              repositoryDir.resolve(exchange.getRequestURI().getPath().substring(1)).normalize();
          if (!file.startsWith(repositoryDir) || !Files.isRegularFile(file)) {
            exchange.sendResponseHeaders(404, -1);
          } else if (exchange.getRequestMethod().equals("HEAD")) {
            exchange.getResponseHeaders().set("Content-Length", Long.toString(Files.size(file)));
            exchange.sendResponseHeaders(200, -1);
          } else {
            exchange.sendResponseHeaders(200, Files.size(file));
            Files.copy(file, exchange.getResponseBody());
          }
          exchange.close();
        });
    repository.start();
    Files.writeString(projectDir.resolve("settings.gradle"), "rootProject.name = 'muzzle-test'\n");
    createTooling();
  }

  @AfterEach
  void tearDown() {
    repository.stop(0);
  }

  @Test
  void defersArtifactResolutionAndReusesConfigurationCache() throws IOException {
    publishLibrary("1.0", true, "");
    publishLibrary("2.0", false, "");
    writeBuild(
        """
        pass {
          group.set("fixture")
          module.set("library")
          versions.set("[1.0,2.0)")
          assertInverse.set(true)
        }
        """,
        """
        gradle.projectsEvaluated {
          tasks.named("muzzle-AssertPass-fixture-library-1.0").get()
          tasks.named("muzzle-AssertFail-fixture-library-2.0").get()
          configurations.matching { it.name.startsWith("muzzle-Assert") }.each {
            assert it.state == org.gradle.api.artifacts.Configuration.State.UNRESOLVED
          }
        }
        """);

    // Aether's first metadata download can invalidate the first configuration-cache entry.
    for (int attempt = 0; attempt < 3; attempt++) {
      BuildResult result = runner().build();
      assertThat(result.task(":muzzle-AssertPass-fixture-library-1.0").getOutcome())
          .isEqualTo(TaskOutcome.SUCCESS);
      assertThat(result.task(":muzzle-AssertFail-fixture-library-2.0").getOutcome())
          .isEqualTo(TaskOutcome.SUCCESS);
      assertThat(result.getOutput()).doesNotContain("Muzzle validation failed");
      if (attempt == 2) {
        assertThat(result.getOutput()).contains("Reusing configuration cache.");
      }
    }
  }

  @Test
  void reportsResolutionFailuresWhenPassAssertionFails() throws IOException {
    assertFailureDiagnostics(true);
  }

  @Test
  void reportsResolutionFailuresWhenFailAssertionFails() throws IOException {
    assertFailureDiagnostics(false);
  }

  private void assertFailureDiagnostics(boolean assertPass) throws IOException {
    publishLibrary(
        "1.0",
        !assertPass,
        """
        <dependencies>
          <dependency>
            <groupId>fixture</groupId>
            <artifactId>missing-metadata</artifactId>
            <version>1.0</version>
          </dependency>
          <dependency>
            <groupId>fixture</groupId>
            <artifactId>missing-artifact</artifactId>
            <version>1.0</version>
          </dependency>
        </dependencies>
        """);
    writePom("missing-artifact", "1.0", "");
    writeBuild(
        """
        %s {
          group.set("fixture")
          module.set("library")
          versions.set("[1.0]")
        }
        """
            .formatted(assertPass ? "pass" : "fail"),
        "");

    for (int attempt = 0; attempt < 3; attempt++) {
      BuildResult result = runner().buildAndFail();
      assertThat(result.getOutput())
          .contains(
              "Muzzle validation failed for :muzzle-Assert"
                  + (assertPass ? "Pass" : "Fail")
                  + "-fixture-library-1.0",
              "expected " + (assertPass ? "acceptance (assertPass)" : "rejection (assertFail)"),
              "Dependency resolution failures (lenient resolution): 2",
              "fixture:missing-metadata:1.0",
              "missing-artifact-1.0.jar",
              "Resolved dependency JARs used for",
              "library-1.0.jar",
              assertPass
                  ? "Instrumentation failed Muzzle validation"
                  : "Instrumentation unexpectedly passed Muzzle validation");
      if (attempt == 2) {
        assertThat(result.getOutput()).contains("Reusing configuration cache.");
      }
    }
  }

  private void writeBuild(String directives, String extra) throws IOException {
    Files.writeString(
        projectDir.resolve("build.gradle"),
        """
        plugins {
          id("io.opentelemetry.instrumentation.muzzle-check")
        }
        repositories {
          maven {
            url = uri("http://127.0.0.1:%d/")
            allowInsecureProtocol = true
          }
        }
        dependencies {
          muzzleTooling(files("tooling.jar"))
        }
        muzzle {
          %s
        }
        %s
        """
            .formatted(repository.getAddress().getPort(), directives, extra));
  }

  private GradleRunner runner() {
    return GradleRunner.create()
        .withProjectDir(projectDir.toFile())
        .withPluginClasspath()
        .withArguments(
            "muzzle",
            "--configuration-cache",
            "--configuration-cache-problems=fail",
            "--console=plain",
            "--stacktrace");
  }

  private void publishLibrary(String version, boolean matches, String dependencies)
      throws IOException {
    Path directory = writePom("library", version, dependencies);
    try (JarOutputStream jar =
        new JarOutputStream(
            Files.newOutputStream(directory.resolve("library-" + version + ".jar")))) {
      if (matches) {
        jar.putNextEntry(new JarEntry("supported.txt"));
        jar.write("supported".getBytes(StandardCharsets.UTF_8));
        jar.closeEntry();
      }
    }
    Files.writeString(
        directory.getParent().resolve("maven-metadata.xml"),
        """
        <metadata>
          <groupId>fixture</groupId>
          <artifactId>library</artifactId>
          <versioning>
            <versions>
              <version>1.0</version>
              <version>2.0</version>
            </versions>
          </versioning>
        </metadata>
        """);
  }

  private Path writePom(String artifact, String version, String dependencies) throws IOException {
    Path directory =
        Files.createDirectories(
            repositoryDir.resolve("fixture").resolve(artifact).resolve(version));
    Files.writeString(
        directory.resolve(artifact + "-" + version + ".pom"),
        """
        <project>
          <modelVersion>4.0.0</modelVersion>
          <groupId>fixture</groupId>
          <artifactId>%s</artifactId>
          <version>%s</version>
          %s
        </project>
        """
            .formatted(artifact, version, dependencies));
    return directory;
  }

  private void createTooling() throws IOException {
    Path source =
        projectDir.resolve(
            "tooling/io/opentelemetry/javaagent/tooling/muzzle/ClassLoaderMatcher.java");
    Files.createDirectories(source.getParent());
    Files.writeString(
        source,
        """
        package io.opentelemetry.javaagent.tooling.muzzle;

        import java.util.Collections;
        import java.util.List;
        import java.util.Map;
        import java.util.Set;

        public class ClassLoaderMatcher {
          public static Map<String, List<String>> matchesAll(
              ClassLoader userClassLoader, boolean assertPass, Set<String> names, Set<String> modules) {
            return Collections.singletonMap(
                "fixture",
                userClassLoader.getResource("supported.txt") == null
                    ? Collections.singletonList("missing supported.txt")
                    : Collections.emptyList());
          }
        }
        """);
    int result =
        ToolProvider.getSystemJavaCompiler()
            .run(null, null, null, "--release", "17", source.toString());
    assertThat(result).isZero();
    try (JarOutputStream jar =
        new JarOutputStream(Files.newOutputStream(projectDir.resolve("tooling.jar")))) {
      jar.putNextEntry(
          new JarEntry("io/opentelemetry/javaagent/tooling/muzzle/ClassLoaderMatcher.class"));
      Files.copy(source.resolveSibling("ClassLoaderMatcher.class"), jar);
      jar.closeEntry();
    }
  }
}
