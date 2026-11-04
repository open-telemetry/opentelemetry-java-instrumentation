/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.muzzle;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.BuildTask;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MuzzleSliceFunctionalTest {
  private static final List<String> CHILDREN = asList("a", "b", "c", "d", "e", "f", "g", "h");
  private final ConcurrentLinkedQueue<String> requests = new ConcurrentLinkedQueue<>();

  @TempDir Path temp;

  private HttpServer server;

  @AfterEach
  void stopRepository() {
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  void exactSlicesDiscoverOnlySelectedProjectsAndReuseConfigurationCache() throws IOException {
    startRepository();
    for (int slice : asList(1, 8)) {
      Path project = fixture("slice-" + slice, true);
      requests.clear();
      String child = slice == 1 ? "a" : "h";
      GradleRunner runner =
          runner(project, (slice == 1 ? ":" : "") + "instrumentation:muzzle" + slice);
      for (int run = 0; run < 3; run++) {
        BuildResult result = runner.build();
        assertThat(assertionTasks(result))
            .containsExactly(
                slice == 1
                    ? ":instrumentation:a:muzzle-AssertPass-fixture-library-a-1.0"
                    : ":instrumentation:h:muzzle-AssertPass-fixture-library-h-1.0");
        assertThat(result.getTasks())
            .filteredOn(task -> task.getPath().contains(":muzzle-Assert"))
            .allSatisfy(task -> assertThat(task.getOutcome()).isEqualTo(TaskOutcome.SUCCESS));
        if (run == 2) {
          assertThat(result.getOutput()).contains("Reusing configuration cache");
        }
      }
      assertThat(requests)
          .anySatisfy(
              path -> assertThat(path).contains("/library-" + child + "/maven-metadata.xml"));
      for (String other : CHILDREN) {
        if (!other.equals(child)) {
          assertThat(project.resolve("instrumentation/" + other + "/build/muzzleRepo"))
              .doesNotExist();
          assertThat(requests)
              .noneSatisfy(
                  path -> assertThat(path).contains("/library-" + other + "/maven-metadata.xml"));
        }
      }
    }
  }

  @Test
  void buildsWithoutSliceAssignmentsDiscoverEveryProject() throws IOException {
    startRepository();
    Path project = fixture("external", false);
    BuildResult result = runner(project, ":instrumentation:muzzle1").build();
    assertThat(assertionTasks(result))
        .containsExactly(":instrumentation:a:muzzle-AssertPass-fixture-library-a-1.0");
    assertAllProjectsDiscovered();
  }

  @Test
  void mixedSelectorsRetainDirectAssertionsOutsideTheSlice() throws IOException {
    startRepository();
    Path project = fixture("mixed", true);
    BuildResult result =
        runner(
                project,
                ":instrumentation:muzzle1",
                ":instrumentation:c:muzzle-AssertPass-fixture-library-c-1.0")
            .build();
    assertThat(assertionTasks(result))
        .containsExactlyInAnyOrder(
            ":instrumentation:a:muzzle-AssertPass-fixture-library-a-1.0",
            ":instrumentation:c:muzzle-AssertPass-fixture-library-c-1.0");
    assertAllProjectsDiscovered();
  }

  @Test
  void bareAssertionSelectorsDiscoverEveryProject() throws IOException {
    startRepository();
    Path project = fixture("direct", true);
    BuildResult result = runner(project, "muzzle-AssertPass-fixture-library-c-1.0").build();
    assertThat(assertionTasks(result))
        .containsExactly(":instrumentation:c:muzzle-AssertPass-fixture-library-c-1.0");
    assertAllProjectsDiscovered();
  }

  @Test
  void selectedSlicePreservesPositiveAndInverseAssertions() throws IOException {
    startRepository();
    publishLibrary("library-a", "2.0");
    Path project = fixture("inverse", true);
    Path build = project.resolve("instrumentation/a/build.gradle");
    Files.writeString(
        build,
        Files.readString(build)
            .replace("versions.set('[1.0]')", "versions.set('[1.0]')\nassertInverse.set(true)"));
    BuildResult result = runner(project, ":instrumentation:muzzle1").build();
    assertThat(assertionTasks(result))
        .containsExactly(
            ":instrumentation:a:muzzle-AssertPass-fixture-library-a-1.0",
            ":instrumentation:a:muzzle-AssertFail-fixture-library-a-2.0");
  }

  private void assertAllProjectsDiscovered() {
    for (String child : CHILDREN) {
      assertThat(requests)
          .anySatisfy(
              path -> assertThat(path).contains("/library-" + child + "/maven-metadata.xml"));
    }
  }

  private static List<String> assertionTasks(BuildResult result) {
    return result.getTasks().stream()
        .map(BuildTask::getPath)
        .filter(path -> path.contains(":muzzle-Assert"))
        .toList();
  }

  private Path fixture(String name, boolean assignSlices) throws IOException {
    Path project = temp.resolve(name);
    Files.createDirectories(project);
    Files.writeString(
        project.resolve("settings.gradle"),
        """
        rootProject.name = 'slice-fixture'
        include 'instrumentation'
        ('a'..'h').each { include "instrumentation:${it}" }
        """);
    Files.writeString(
        project.resolve("build.gradle"),
        """
        plugins { id 'io.opentelemetry.instrumentation.muzzle-check' apply false }
        project(':instrumentation') {
          def slices = (1..8).collect { tasks.register("muzzle${it}") }
          def counter = 0
          subprojects { child ->
            child.plugins.withId('io.opentelemetry.instrumentation.muzzle-check') {
              def sliceIndex = counter++ %% slices.size()
              %s
              slices[sliceIndex].configure { dependsOn(child.tasks.named('muzzle')) }
            }
          }
        }
        """
            .formatted(assignSlices ? "child.ext.otelMuzzleSlice = sliceIndex + 1" : ""));
    for (String child : CHILDREN) {
      Path module = project.resolve("instrumentation/" + child);
      Path source =
          module.resolve(
              "src/main/java/io/opentelemetry/javaagent/tooling/muzzle/ClassLoaderMatcher.java");
      Files.createDirectories(source.getParent());
      Files.writeString(
          module.resolve("build.gradle"),
          """
          plugins { id 'io.opentelemetry.instrumentation.muzzle-check' }
          repositories {
            maven {
              url = uri('http://127.0.0.1:%d/')
              allowInsecureProtocol = true
            }
          }
          muzzle {
            pass {
              group.set('fixture')
              module.set('library-%s')
              versions.set('[1.0]')
            }
          }
          """
              .formatted(server.getAddress().getPort(), child));
      Files.writeString(
          source,
          """
          package io.opentelemetry.javaagent.tooling.muzzle;
          import java.util.*;
          public class ClassLoaderMatcher {
            public static Map<String, List<String>> matchesAll(
                ClassLoader loader, boolean inject, Set<String> names, Set<String> modules) {
              if (loader.getResource("probe.txt") == null) {
                throw new IllegalStateException("Target library is missing");
              }
              return Map.of("fixture", inject ? List.of() : List.of("expected mismatch"));
            }
          }
          """);
    }
    return project;
  }

  private static GradleRunner runner(Path project, String... selectors) {
    List<String> arguments = new ArrayList<>(asList(selectors));
    arguments.addAll(
        asList("--build-cache", "--configuration-cache", "--stacktrace", "--max-workers=2"));
    return GradleRunner.create()
        .withProjectDir(project.toFile())
        .withPluginClasspath()
        .withArguments(arguments);
  }

  private void startRepository() throws IOException {
    for (String child : CHILDREN) {
      publishLibrary("library-" + child, "1.0");
    }
    Path repository = temp.resolve("repository");
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/",
        exchange -> {
          try {
            requests.add(exchange.getRequestURI().getPath());
            Path file =
                repository.resolve(exchange.getRequestURI().getPath().substring(1)).normalize();
            if (!file.startsWith(repository) || !Files.isRegularFile(file)) {
              exchange.sendResponseHeaders(404, -1);
              return;
            }
            byte[] content = Files.readAllBytes(file);
            exchange.sendResponseHeaders(
                200, exchange.getRequestMethod().equals("HEAD") ? -1 : content.length);
            if (!exchange.getRequestMethod().equals("HEAD")) {
              exchange.getResponseBody().write(content);
            }
          } finally {
            exchange.close();
          }
        });
    server.start();
  }

  private void publishLibrary(String module, String version) throws IOException {
    Path artifact = temp.resolve("repository/fixture/" + module);
    Path library = artifact.resolve(version + "/" + module + "-" + version + ".jar");
    Files.createDirectories(library.getParent());
    try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(library))) {
      JarEntry entry = new JarEntry("probe.txt");
      entry.setTime(0);
      jar.putNextEntry(entry);
      jar.write("library".getBytes(UTF_8));
      jar.closeEntry();
    }
    Files.writeString(
        library.resolveSibling(module + "-" + version + ".pom"),
        """
        <project><modelVersion>4.0.0</modelVersion><groupId>fixture</groupId>
        <artifactId>%s</artifactId><version>%s</version></project>
        """
            .formatted(module, version));
    Files.writeString(
        artifact.resolve("maven-metadata.xml"),
        """
        <metadata><groupId>fixture</groupId><artifactId>%s</artifactId>
        <versioning><versions><version>1.0</version>%s</versions></versioning></metadata>
        """
            .formatted(module, version.equals("1.0") ? "" : "<version>" + version + "</version>"));
  }
}
