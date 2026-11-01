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

class MuzzleCacheFunctionalTest {
  private static final String ASSERTION = ":muzzle-AssertPass-fixture-library-1.0";

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
  void cachedAssertionsAreRelocatableAndInvalidateMatchingInputs() throws IOException {
    startRepository();
    Path first = fixture("first");
    assertThat(build(first, true).task(ASSERTION).getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
    assertThat(build(first, true).task(ASSERTION).getOutcome()).isEqualTo(TaskOutcome.UP_TO_DATE);

    Path second = fixture("second");
    assertThat(build(second, true).task(ASSERTION).getOutcome()).isEqualTo(TaskOutcome.FROM_CACHE);
    Path report =
        MuzzleBenchmarkSupport.reportFile(second.resolve("reports").toFile(), ASSERTION).toPath();
    assertThat(Files.readAllLines(report)).startsWith(ASSERTION, "assertPass\ttrue");

    Files.writeString(second.resolve("src/main/resources/module.txt"), "changed");
    assertThat(build(second, true).task(ASSERTION).getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
    Files.writeString(second.resolve("tooling.txt"), "changed");
    assertThat(build(second, true).task(ASSERTION).getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
    Files.writeString(second.resolve("bootstrap.txt"), "changed");
    assertThat(build(second, true).task(ASSERTION).getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
    assertThat(build(second, true, "-PexcludedName=other").task(ASSERTION).getOutcome())
        .isEqualTo(TaskOutcome.SUCCESS);
    assertThat(
            build(second, true, "-PexcludedName=other", "-Djava.vendor=cache-fixture")
                .task(ASSERTION)
                .getOutcome())
        .isEqualTo(TaskOutcome.SUCCESS);

    Path library = temp.resolve("repository/fixture/library/1.0/library-1.0.jar");
    writeLibrary(library, "changed library");
    assertThat(build(second, true, "--refresh-dependencies").task(ASSERTION).getOutcome())
        .isEqualTo(TaskOutcome.SUCCESS);
  }

  @Test
  void normalAssertionsStillExecuteAndFailedAssertionsAreNotCached() throws IOException {
    startRepository();
    Path project = fixture("normal");
    assertThat(build(project, false).task(ASSERTION).getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
    assertThat(build(project, false).task(ASSERTION).getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
    assertThat(build(project, true).task(ASSERTION).getOutcome()).isEqualTo(TaskOutcome.SUCCESS);

    Files.writeString(project.resolve("src/main/resources/module.txt"), "broken");
    for (int i = 0; i < 2; i++) {
      assertThat(runner(project, true).buildAndFail().task(ASSERTION).getOutcome())
          .isEqualTo(TaskOutcome.FAILED);
      assertThat(MuzzleBenchmarkSupport.reportFile(project.resolve("reports").toFile(), ASSERTION))
          .doesNotExist();
    }
  }

  @Test
  void negativeAssertionsAreCached() throws IOException {
    startRepository();
    String assertion = ":muzzle-AssertFail-fixture-library-1.0";
    Path first = fixture("negative-first", false);
    assertThat(build(first, true).task(assertion).getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
    Path second = fixture("negative-second", false);
    assertThat(build(second, true).task(assertion).getOutcome()).isEqualTo(TaskOutcome.FROM_CACHE);
    Path report =
        MuzzleBenchmarkSupport.reportFile(second.resolve("reports").toFile(), assertion).toPath();
    assertThat(Files.readAllLines(report)).startsWith(assertion, "assertPass\tfalse");
  }

  @Test
  void missingTransitivesArePartOfTheCachedWorkload() throws IOException {
    startRepository();
    Path pom = temp.resolve("repository/fixture/library/1.0/library-1.0.pom");
    Files.writeString(
        pom,
        """
        <project><modelVersion>4.0.0</modelVersion><groupId>fixture</groupId>
        <artifactId>library</artifactId><version>1.0</version>
        <dependencies><dependency><groupId>fixture</groupId><artifactId>missing</artifactId>
        <version>1.0</version></dependency></dependencies></project>
        """);
    Path first = fixture("missing-first");
    assertThat(build(first, true).task(ASSERTION).getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
    Path second = fixture("missing-second");
    assertThat(build(second, true).task(ASSERTION).getOutcome()).isEqualTo(TaskOutcome.FROM_CACHE);
    Path report =
        MuzzleBenchmarkSupport.reportFile(second.resolve("reports").toFile(), ASSERTION).toPath();
    assertThat(Files.readAllLines(report))
        .anySatisfy(line -> assertThat(line).startsWith("unresolved\t"));

    Path missing = temp.resolve("repository/fixture/missing/1.0/missing-1.0.jar");
    Files.createDirectories(missing.getParent());
    writeLibrary(missing, "transitive");
    Files.writeString(
        missing.resolveSibling("missing-1.0.pom"),
        """
        <project><modelVersion>4.0.0</modelVersion><groupId>fixture</groupId>
        <artifactId>missing</artifactId><version>1.0</version></project>
        """);
    assertThat(build(second, true, "--refresh-dependencies").task(ASSERTION).getOutcome())
        .isEqualTo(TaskOutcome.SUCCESS);
    assertThat(Files.readAllLines(report))
        .noneSatisfy(line -> assertThat(line).startsWith("unresolved\t"));
  }

  @Test
  void slicesDoNotDiscoverVersionsForUnselectedProjects() throws IOException {
    startRepository();
    Path project = slicedFixture();
    String assertion = ":instrumentation:a:muzzle-AssertPass-fixture-library-1.0";
    // The first discovery creates metadata files tracked by the configuration cache.
    for (int i = 0; i < 3; i++) {
      BuildResult result = slicedRunner(project, "instrumentation:muzzle1").build();
      assertThat(result.task(assertion).getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
      assertThat(result.getTasks())
          .filteredOn(task -> task.getPath().contains(":muzzle-Assert"))
          .extracting(BuildTask::getPath)
          .containsExactly(assertion);
      if (i == 2) {
        assertThat(result.getOutput()).contains("Reusing configuration cache");
      }
    }
    assertThat(requests)
        .anySatisfy(path -> assertThat(path).contains("/library/maven-metadata.xml"))
        .noneSatisfy(path -> assertThat(path).contains("/module-"));
  }

  @Test
  void multipleSlicesAndDirectTasksPreserveSelectedChecks() throws IOException {
    startRepository();
    publishLibrary("module-b");
    publishLibrary("module-c");
    Path project = slicedFixture();
    BuildResult result =
        slicedRunner(project, ":instrumentation:muzzle1", ":instrumentation:muzzle2").build();
    assertThat(result.getTasks())
        .filteredOn(task -> task.getPath().contains(":muzzle-Assert"))
        .extracting(BuildTask::getPath)
        .containsExactlyInAnyOrder(
            ":instrumentation:a:muzzle-AssertPass-fixture-library-1.0",
            ":instrumentation:b:muzzle-AssertPass-fixture-module-b-1.0");
    assertThat(requests)
        .allSatisfy(path -> assertThat(path).doesNotContain("/module-c/", "/module-d/"));

    requests.clear();
    result = slicedRunner(project, ":instrumentation:muzzle1", ":instrumentation:c:muzzle").build();
    assertThat(result.getTasks())
        .filteredOn(task -> task.getPath().contains(":muzzle-Assert"))
        .extracting(BuildTask::getPath)
        .containsExactlyInAnyOrder(
            ":instrumentation:a:muzzle-AssertPass-fixture-library-1.0",
            ":instrumentation:c:muzzle-AssertPass-fixture-module-c-1.0");
    assertThat(requests)
        .anySatisfy(path -> assertThat(path).contains("/module-c/maven-metadata.xml"))
        .noneSatisfy(path -> assertThat(path).contains("/module-d/"));

    publishLibrary("module-d");
    result =
        slicedRunner(
                project,
                ":instrumentation:muzzle1",
                ":instrumentation:muzzle2",
                ":instrumentation:muzzle3",
                ":instrumentation:muzzle4")
            .build();
    assertThat(result.getTasks())
        .filteredOn(task -> task.getPath().contains(":muzzle-Assert"))
        .extracting(BuildTask::getPath)
        .containsExactlyInAnyOrder(
            ":instrumentation:a:muzzle-AssertPass-fixture-library-1.0",
            ":instrumentation:b:muzzle-AssertPass-fixture-module-b-1.0",
            ":instrumentation:c:muzzle-AssertPass-fixture-module-c-1.0",
            ":instrumentation:d:muzzle-AssertPass-fixture-module-d-1.0");
  }

  private GradleRunner slicedRunner(Path project, String... tasks) {
    GradleRunner runner = runner(project, false);
    List<String> arguments = new ArrayList<>(asList(tasks));
    arguments.addAll(runner.getArguments().subList(1, runner.getArguments().size()));
    return runner.withArguments(arguments);
  }

  private Path slicedFixture() throws IOException {
    Path project = temp.resolve("sliced");
    fixture("sliced/instrumentation/a", true, "library");
    for (String name : asList("b", "c", "d")) {
      fixture("sliced/instrumentation/" + name, true, "module-" + name);
    }
    Files.writeString(
        project.resolve("settings.gradle"),
        """
        rootProject.name = 'slice-fixture'
        include 'instrumentation', 'instrumentation:a', 'instrumentation:b',
          'instrumentation:c', 'instrumentation:d'
        """);
    Files.writeString(
        project.resolve("build.gradle"),
        """
        plugins { id 'io.opentelemetry.instrumentation.muzzle-check' apply false }
        project(':instrumentation') {
          def slices = (1..4).collect { tasks.register("muzzle${it}") }
          def counter = 0
          subprojects { child ->
            child.plugins.withId('io.opentelemetry.instrumentation.muzzle-check') {
              slices[counter++ % 4].configure { dependsOn(child.tasks.named('muzzle')) }
            }
          }
        }
        """);
    return project;
  }

  private BuildResult build(Path project, boolean cache, String... arguments) {
    return runner(project, cache, arguments).build();
  }

  private GradleRunner runner(Path project, boolean cache, String... arguments) {
    List<String> command =
        new ArrayList<>(
            asList(
                "muzzle",
                "--build-cache",
                "--configuration-cache",
                "--stacktrace",
                "--max-workers=2",
                "-PmuzzleBenchmarkSeed=1729",
                "-PmuzzleBenchmarkCache=" + cache,
                "-PmuzzleBenchmarkReportDirectory=" + project.resolve("reports")));
    command.addAll(asList(arguments));
    return GradleRunner.create()
        .withProjectDir(project.toFile())
        .withPluginClasspath()
        .withArguments(command);
  }

  private Path fixture(String name) throws IOException {
    return fixture(name, true);
  }

  private Path fixture(String name, boolean assertPass) throws IOException {
    return fixture(name, assertPass, "library");
  }

  private Path fixture(String name, boolean assertPass, String module) throws IOException {
    Path project = temp.resolve(name);
    Files.createDirectories(
        project.resolve("src/main/java/io/opentelemetry/javaagent/tooling/muzzle"));
    Files.createDirectories(project.resolve("src/main/resources"));
    Files.writeString(
        project.resolve("settings.gradle"),
        """
        rootProject.name = 'cache-fixture'
        buildCache { local { directory = file('../build-cache') } }
        """);
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
          %s {
            group.set('fixture')
            module.set('%s')
            versions.set('[1.0]')
            excludeInstrumentationName(providers.gradleProperty('excludedName').getOrElse('none'))
          }
        }
        tasks.named('shadowMuzzleTooling') { from('tooling.txt') }
        tasks.named('shadowMuzzleBootstrap') { from('bootstrap.txt') }
        """
            .formatted(server.getAddress().getPort(), assertPass ? "pass" : "fail", module));
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
            try (var input = ClassLoaderMatcher.class.getClassLoader().getResourceAsStream("module.txt")) {
              if (new String(input.readAllBytes(), "UTF-8").equals("broken")) {
                throw new IllegalStateException("Fixture assertion failed");
              }
            }
            return Map.of("fixture", inject ? List.of() : List.of("expected mismatch"));
          }
        }
        """);
    Files.writeString(project.resolve("src/main/resources/module.txt"), "module");
    Files.writeString(project.resolve("tooling.txt"), "tooling");
    Files.writeString(project.resolve("bootstrap.txt"), "bootstrap");
    return project;
  }

  private void startRepository() throws IOException {
    publishLibrary("library");
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

  private void publishLibrary(String module) throws IOException {
    Path library = temp.resolve("repository/fixture/" + module + "/1.0/" + module + "-1.0.jar");
    Files.createDirectories(library.getParent());
    writeLibrary(library, "library");
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

  private static void writeLibrary(Path file, String content) throws IOException {
    try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(file))) {
      JarEntry entry = new JarEntry("probe.txt");
      entry.setTime(0);
      jar.putNextEntry(entry);
      jar.write(content.getBytes(UTF_8));
      jar.closeEntry();
    }
  }
}
