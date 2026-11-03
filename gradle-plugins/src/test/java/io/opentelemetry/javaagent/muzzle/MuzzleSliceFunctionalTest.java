/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.muzzle;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;
import static java.util.stream.Collectors.joining;
import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.BuildTask;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MuzzleSliceFunctionalTest {
  private final List<String> requests = new CopyOnWriteArrayList<>();

  @TempDir Path temp;

  private HttpServer server;

  @AfterEach
  void stopRepository() {
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  void fourAndEightSlicesPreserveCoverageAndPairAssignments() throws IOException {
    startRepository();
    Path four = slicedFixture("four", 8);
    BuildResult fourAll =
        slicedRunner(
                four,
                ":instrumentation:muzzle1",
                ":instrumentation:muzzle2",
                ":instrumentation:muzzle3",
                ":instrumentation:muzzle4")
            .build();

    Path eight = slicedFixture("eight", 8);
    BuildResult eightAll =
        slicedRunner(
                eight,
                ":instrumentation:muzzle1",
                ":instrumentation:muzzle2",
                ":instrumentation:muzzle3",
                ":instrumentation:muzzle4",
                ":instrumentation:muzzle5",
                ":instrumentation:muzzle6",
                ":instrumentation:muzzle7",
                ":instrumentation:muzzle8",
                "-PmuzzleSlices=8")
            .build();
    assertThat(assertionPaths(fourAll))
        .containsExactlyInAnyOrderElementsOf(assertionPaths(eightAll))
        .hasSize(8);

    BuildResult fourSlice =
        slicedRunner(slicedFixture("four-pair", 8), ":instrumentation:muzzle2").build();
    BuildResult eightPair =
        slicedRunner(
                slicedFixture("eight-pair", 8),
                ":instrumentation:muzzle2",
                ":instrumentation:muzzle6",
                "-PmuzzleSlices=8")
            .build();
    assertThat(assertionPaths(fourSlice))
        .containsExactlyInAnyOrderElementsOf(assertionPaths(eightPair))
        .containsExactlyInAnyOrder(
            ":instrumentation:b:muzzle-AssertPass-fixture-module-b-1.0",
            ":instrumentation:f:muzzle-AssertPass-fixture-module-f-1.0");
  }

  @Test
  void selectedSlicesSkipOtherProjectsAndReuseConfigurationCache() throws IOException {
    startRepository();
    Path project = slicedFixture("discovery", 4);
    String assertion = ":instrumentation:a:muzzle-AssertPass-fixture-library-1.0";
    for (int i = 0; i < 3; i++) {
      BuildResult result =
          slicedRunner(project, ":instrumentation:muzzle1", "--configuration-cache").build();
      assertThat(assertionPaths(result)).containsExactly(assertion);
      if (i == 2) {
        assertThat(result.getOutput()).contains("Reusing configuration cache");
      }
    }
    assertThat(requests)
        .anySatisfy(path -> assertThat(path).contains("/library/maven-metadata.xml"))
        .noneSatisfy(path -> assertThat(path).contains("/module-"));
  }

  @Test
  void directAndMixedRequestsDiscoverOnlyTheirProjects() throws IOException {
    startRepository();
    Path mixed = slicedFixture("mixed", 4);
    BuildResult mixedResult =
        slicedRunner(mixed, ":instrumentation:muzzle1", ":instrumentation:c:muzzle").build();
    assertThat(assertionPaths(mixedResult))
        .containsExactlyInAnyOrder(
            ":instrumentation:a:muzzle-AssertPass-fixture-library-1.0",
            ":instrumentation:c:muzzle-AssertPass-fixture-module-c-1.0");
    assertThat(requests)
        .anySatisfy(path -> assertThat(path).contains("/library/maven-metadata.xml"))
        .anySatisfy(path -> assertThat(path).contains("/module-c/maven-metadata.xml"))
        .allSatisfy(path -> assertThat(path).doesNotContain("/module-b/", "/module-d/"));

    requests.clear();
    Path direct = slicedFixture("direct", 4);
    BuildResult directResult =
        slicedRunner(direct, ":instrumentation:c:muzzle-AssertPass-fixture-module-c-1.0").build();
    assertThat(assertionPaths(directResult))
        .containsExactly(":instrumentation:c:muzzle-AssertPass-fixture-module-c-1.0");
    assertThat(requests)
        .anySatisfy(path -> assertThat(path).contains("/module-c/maven-metadata.xml"))
        .allSatisfy(
            path -> assertThat(path).doesNotContain("/library/", "/module-b/", "/module-d/"));

    requests.clear();
    BuildResult help = slicedRunner(slicedFixture("non-muzzle", 4), "help").build();
    assertThat(help.getOutput()).contains("BUILD SUCCESSFUL");
    assertThat(requests).isEmpty();
  }

  @Test
  void unqualifiedAssertionRequestDiscoversTaskFromFixtureRoot() throws IOException {
    startRepository();
    BuildResult result =
        slicedRunner(
                slicedFixture("unqualified", 4),
                "muzzle-AssertPass-fixture-module-c-1.0")
            .build();

    assertThat(assertionPaths(result))
        .containsExactly(":instrumentation:c:muzzle-AssertPass-fixture-module-c-1.0");
  }

  @Test
  void externalPluginSupportsModuleAndQualifiedAssertionRequests() throws IOException {
    startRepository();
    Path project = externalPluginFixture("external", "module-c");
    String assertion = ":instrumentation:muzzle-AssertPass-fixture-module-c-1.0";

    BuildResult module = slicedRunner(project, ":instrumentation:muzzle").build();
    assertThat(assertionPaths(module)).containsExactly(assertion);

    BuildResult direct = slicedRunner(project, assertion).build();
    assertThat(assertionPaths(direct)).containsExactly(assertion);
  }

  @Test
  void relativeAssertionRequestResolvesFromInstrumentationDirectory() throws IOException {
    startRepository();
    Path project = slicedFixture("relative", 4);
    BuildResult result =
        slicedRunner(
                project.resolve("instrumentation"),
                "c:muzzle-AssertPass-fixture-module-c-1.0")
            .build();

    assertThat(assertionPaths(result))
        .containsExactly(":instrumentation:c:muzzle-AssertPass-fixture-module-c-1.0");
  }

  @Test
  void unsupportedSliceCountIsRejected() throws IOException {
    startRepository();
    Path project = slicedFixture("invalid", 4);
    BuildResult result =
        slicedRunner(project, ":instrumentation:muzzle1", "-PmuzzleSlices=6").buildAndFail();
    assertThat(result.getOutput()).contains("muzzleSlices must be 4 or 8");
  }

  private static List<String> assertionPaths(BuildResult result) {
    return result.getTasks().stream()
        .map(BuildTask::getPath)
        .filter(path -> path.contains(":muzzle-Assert"))
        .toList();
  }

  private GradleRunner slicedRunner(Path project, String... arguments) {
    List<String> command = new ArrayList<>(asList(arguments));
    command.addAll(asList("--stacktrace", "--max-workers=2"));
    return GradleRunner.create()
        .withProjectDir(project.toFile())
        .withPluginClasspath()
        .withArguments(command);
  }

  private Path slicedFixture(String name, int projectCount) throws IOException {
    Path project = temp.resolve(name);
    List<String> names = asList("a", "b", "c", "d", "e", "f", "g", "h").subList(0, projectCount);
    for (String child : names) {
      fixture(
          name + "/instrumentation/" + child, child.equals("a") ? "library" : "module-" + child);
    }
    Files.writeString(
        project.resolve("settings.gradle"),
        "rootProject.name = 'slice-fixture'\ninclude 'instrumentation', "
            + names.stream()
                .map(child -> "'instrumentation:" + child + "'")
                .collect(joining(", ")));
    Files.writeString(
        project.resolve("build.gradle"),
        """
        plugins { id 'io.opentelemetry.instrumentation.muzzle-check' apply false }
        project(':instrumentation') {
          def count = providers.gradleProperty('muzzleSlices').map { it.toInteger() }.getOrElse(4)
          if (!(count in [4, 8])) throw new GradleException('muzzleSlices must be 4 or 8')
          def slices = (1..count).collect { tasks.register("muzzle${it}") }
          def counter = 0
          subprojects { child ->
            child.plugins.withId('io.opentelemetry.instrumentation.muzzle-check') {
              slices[counter++ % count].configure { dependsOn(child.tasks.named('muzzle')) }
            }
          }
        }
        """);
    return project;
  }

  private Path externalPluginFixture(String name, String module) throws IOException {
    Path project = temp.resolve(name);
    fixture(name + "/instrumentation", module);
    Files.writeString(
        project.resolve("settings.gradle"),
        "rootProject.name = 'external-fixture'\ninclude 'instrumentation'");
    return project;
  }

  private void fixture(String name, String module) throws IOException {
    Path project = temp.resolve(name);
    Files.createDirectories(
        project.resolve("src/main/java/io/opentelemetry/javaagent/tooling/muzzle"));
    Files.createDirectories(project.resolve("src/main/resources"));
    Files.writeString(
        project.resolve("build.gradle"),
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
            module.set('%s')
            versions.set('[1.0]')
          }
        }
        tasks.named('shadowMuzzleTooling') { from('tooling.txt') }
        tasks.named('shadowMuzzleBootstrap') { from('bootstrap.txt') }
        """
            .formatted(server.getAddress().getPort(), module));
    Files.writeString(
        project.resolve(
            "src/main/java/io/opentelemetry/javaagent/tooling/muzzle/ClassLoaderMatcher.java"),
        """
        package io.opentelemetry.javaagent.tooling.muzzle;
        import java.util.*;
        public class ClassLoaderMatcher {
          public static Map<String, List<String>> matchesAll(
              ClassLoader loader, boolean inject, Set<String> names, Set<String> modules)
              throws Exception {
            if (loader.getResource("probe.txt") == null) {
              throw new IllegalStateException("Target library is missing");
            }
            return Map.of("fixture", inject ? List.of() : List.of("expected mismatch"));
          }
        }
        """);
    Files.writeString(project.resolve("src/main/resources/module.txt"), "module");
    Files.writeString(project.resolve("tooling.txt"), "tooling");
    Files.writeString(project.resolve("bootstrap.txt"), "bootstrap");
  }

  private void startRepository() throws IOException {
    Path repository = temp.resolve("repository");
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/",
        exchange -> {
          try {
            String requestPath = exchange.getRequestURI().getPath();
            requests.add(requestPath);
            Path file = repository.resolve(requestPath.substring(1)).normalize();
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
    for (String module :
        asList(
            "library",
            "module-b",
            "module-c",
            "module-d",
            "module-e",
            "module-f",
            "module-g",
            "module-h")) {
      publishLibrary(module);
    }
  }

  private void publishLibrary(String module) throws IOException {
    Path library = temp.resolve("repository/fixture/" + module + "/1.0/" + module + "-1.0.jar");
    Files.createDirectories(library.getParent());
    try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(library))) {
      JarEntry entry = new JarEntry("probe.txt");
      entry.setTime(0);
      jar.putNextEntry(entry);
      jar.write("library".getBytes(UTF_8));
      jar.closeEntry();
    }
    Files.writeString(
        library.resolveSibling(module + "-1.0.pom"),
        """
        <project><modelVersion>4.0.0</modelVersion><groupId>fixture</groupId>
        <artifactId>%s</artifactId><version>1.0</version></project>
        """
            .formatted(module));
    Files.writeString(
        library.getParent().getParent().resolve("maven-metadata.xml"),
        """
        <metadata><groupId>fixture</groupId><artifactId>%s</artifactId>
        <versioning><versions><version>1.0</version></versions></versioning></metadata>
        """
            .formatted(module));
  }
}
