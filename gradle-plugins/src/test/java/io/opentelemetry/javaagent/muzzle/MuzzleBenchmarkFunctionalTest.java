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

class MuzzleBenchmarkFunctionalTest {
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
  void normalAssertionsExecuteOnEveryRunIncludingConfigurationCacheReuse() throws IOException {
    startRepository();
    Path project = fixture("normal", true, "library");
    for (int i = 0; i < 3; i++) {
      BuildResult result = runner(project, false).build();
      assertThat(result.task(ASSERTION).getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
      if (i == 2) {
        assertThat(result.getOutput()).contains("Reusing configuration cache");
      }
    }
    assertThat(project.resolve("reports")).doesNotExist();
  }

  @Test
  void recordingPreservesPositiveAndNegativeAssertionsWithoutCachingThem() throws IOException {
    startRepository();
    for (boolean assertPass : asList(true, false)) {
      Path project = fixture(assertPass ? "positive" : "negative", assertPass, "library");
      String assertion = ":muzzle-Assert" + (assertPass ? "Pass" : "Fail") + "-fixture-library-1.0";
      for (int i = 0; i < 3; i++) {
        BuildResult result = runner(project, true).build();
        assertThat(result.task(assertion).getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        if (i == 2) {
          assertThat(result.getOutput()).contains("Reusing configuration cache");
        }
      }
      assertThat(
              Files.readAllLines(
                  MuzzleBenchmarkSupport.reportFile(project.resolve("reports").toFile(), assertion)
                      .toPath()))
          .startsWith(assertion, "assertPass\t" + assertPass)
          .anySatisfy(line -> assertThat(line).contains("fixture:library:1.0", "library-1.0.jar"));
    }
  }

  @Test
  void failedAssertionsCannotLeaveASuccessfulRecord() throws IOException {
    startRepository();
    Path project = fixture("failure", true, "library");
    assertThat(runner(project, true).build().task(ASSERTION).getOutcome())
        .isEqualTo(TaskOutcome.SUCCESS);
    Files.writeString(project.resolve("src/main/resources/module.txt"), "broken");
    assertThat(runner(project, true).buildAndFail().task(ASSERTION).getOutcome())
        .isEqualTo(TaskOutcome.FAILED);
    assertThat(MuzzleBenchmarkSupport.reportFile(project.resolve("reports").toFile(), ASSERTION))
        .doesNotExist();
  }

  @Test
  void missingTransitivesAreRecordedWithoutChangingLenientResolution() throws IOException {
    startRepository();
    Files.writeString(
        temp.resolve("repository/fixture/library/1.0/library-1.0.pom"),
        """
        <project><modelVersion>4.0.0</modelVersion><groupId>fixture</groupId>
        <artifactId>library</artifactId><version>1.0</version>
        <dependencies><dependency><groupId>fixture</groupId><artifactId>missing</artifactId>
        <version>1.0</version></dependency></dependencies></project>
        """);
    Path project = fixture("missing", true, "library");
    assertThat(runner(project, true).build().task(ASSERTION).getOutcome())
        .isEqualTo(TaskOutcome.SUCCESS);
    assertThat(
            Files.readAllLines(
                MuzzleBenchmarkSupport.reportFile(project.resolve("reports").toFile(), ASSERTION)
                    .toPath()))
        .anySatisfy(line -> assertThat(line).startsWith("unresolved\t"));
  }

  @Test
  void reportingRequiresDeterministicSampling() throws IOException {
    startRepository();
    Path project = fixture("unseeded-report", true, "library");
    assertThat(
            runner(project, false, "-PmuzzleBenchmarkReportDirectory=" + project.resolve("reports"))
                .buildAndFail()
                .getOutput())
        .contains("Muzzle benchmark reporting requires muzzleBenchmarkSeed");
  }

  @Test
  void seededSamplesIgnoreReleasesAboveThePinAndIneligibleVersions() throws IOException {
    startRepository();
    Path pinned = temp.resolve(".github/config/latest-dep-versions.json");
    Files.createDirectories(pinned.getParent());
    Files.writeString(pinned, "{\"fixture:library#+\":\"4.0\"}");
    Path metadata = temp.resolve("repository/fixture/library/maven-metadata.xml");
    Files.writeString(
        metadata,
        "<metadata><versioning><versions><version>1.0</version><version>2.0</version>"
            + "<version>3.0</version><version>4.0</version></versions></versioning></metadata>");
    Path first = fixture("sampling-first", true, "library");
    useOpenRange(first);
    BuildResult before =
        runner(first, true, "--dry-run", "-Dotel.javaagent.muzzle.versions.limit=3").build();
    List<String> expected =
        before
            .getOutput()
            .lines()
            .filter(line -> line.startsWith(":muzzle-Assert"))
            .sorted()
            .toList();
    assertThat(expected)
        .hasSize(3)
        .contains(
            ":muzzle-AssertPass-fixture-library-1.0 SKIPPED",
            ":muzzle-AssertPass-fixture-library-4.0 SKIPPED");
    Files.writeString(
        metadata,
        "<metadata><versioning><versions><version>1.0</version><version>2.0</version>"
            + "<version>2.5-rc1</version><version>3.0</version><version>4.0</version>"
            + "<version>5.0</version><version>6.0</version></versions></versioning></metadata>");
    Path second = fixture("sampling-second", true, "library");
    useOpenRange(second);
    BuildResult after =
        runner(second, true, "--dry-run", "-Dotel.javaagent.muzzle.versions.limit=3").build();
    assertThat(
            after
                .getOutput()
                .lines()
                .filter(line -> line.startsWith(":muzzle-Assert"))
                .sorted()
                .toList())
        .containsExactlyElementsOf(expected);
  }

  @Test
  void normalAndSeededSlicesDiscoverEveryProjectBeforeProjectsEvaluated() throws IOException {
    startRepository();
    for (String name : asList("b", "c", "d")) {
      publishLibrary("module-" + name);
    }
    for (boolean benchmark : asList(false, true)) {
      Path project = slicedFixture(benchmark ? "seeded-slice" : "normal-slice");
      requests.clear();
      GradleRunner runner = runner(project, benchmark);
      List<String> arguments = new ArrayList<>(runner.getArguments());
      arguments.set(0, ":instrumentation:muzzle1");
      BuildResult result = runner.withArguments(arguments).build();
      assertThat(result.getTasks())
          .filteredOn(task -> task.getPath().contains(":muzzle-Assert"))
          .extracting(BuildTask::getPath)
          .containsExactly(":instrumentation:a:muzzle-AssertPass-fixture-library-1.0");
      for (String module : asList("library", "module-b", "module-c", "module-d")) {
        assertThat(requests)
            .anySatisfy(path -> assertThat(path).contains("/" + module + "/maven-metadata.xml"));
      }
    }
  }

  @Test
  void assignedSlicePreservesPositiveAndInverseAssertions() throws IOException {
    startRepository();
    Path secondJar = temp.resolve("repository/fixture/library/2.0/library-2.0.jar");
    Files.createDirectories(secondJar.getParent());
    Files.copy(temp.resolve("repository/fixture/library/1.0/library-1.0.jar"), secondJar);
    Files.writeString(
        secondJar.resolveSibling("library-2.0.pom"),
        """
        <project><modelVersion>4.0.0</modelVersion><groupId>fixture</groupId>
        <artifactId>library</artifactId><version>2.0</version></project>
        """);
    Files.writeString(
        secondJar.getParent().getParent().resolve("maven-metadata.xml"),
        """
        <metadata><groupId>fixture</groupId><artifactId>library</artifactId>
        <versioning><versions><version>1.0</version><version>2.0</version></versions></versioning>
        </metadata>
        """);
    for (boolean benchmark : asList(false, true)) {
      Path project = slicedFixture("inverse-" + benchmark, true);
      Path build = project.resolve("instrumentation/a/build.gradle");
      Files.writeString(
          build,
          Files.readString(build)
              .replace("versions.set('[1.0]')", "versions.set('[1.0]')\nassertInverse.set(true)"));
      GradleRunner runner = runner(project, benchmark);
      List<String> arguments = new ArrayList<>(runner.getArguments());
      arguments.set(0, ":instrumentation:muzzle1");
      BuildResult result = runner.withArguments(arguments).build();
      assertThat(result.getTasks())
          .filteredOn(task -> task.getPath().contains(":muzzle-Assert"))
          .extracting(BuildTask::getPath)
          .containsExactly(
              ":instrumentation:a:muzzle-AssertPass-fixture-library-1.0",
              ":instrumentation:a:muzzle-AssertFail-fixture-library-2.0");
    }
  }

  @Test
  void assignedSlicesDiscoverOnlySelectedProjectsAndReuseConfigurationCache() throws IOException {
    startRepository();
    for (String child : asList("b", "c", "d")) {
      publishLibrary("module-" + child);
    }
    for (boolean benchmark : asList(false, true)) {
      for (int slice : asList(1, 3)) {
        Path project = slicedFixture("assigned-" + benchmark + "-" + slice, true);
        requests.clear();
        GradleRunner runner = runner(project, benchmark);
        List<String> arguments = new ArrayList<>(runner.getArguments());
        arguments.set(0, (slice == 1 ? ":" : "") + "instrumentation:muzzle" + slice);
        runner.withArguments(arguments);
        for (int run = 0; run < 3; run++) {
          BuildResult result = runner.build();
          assertThat(result.getTasks())
              .filteredOn(task -> task.getPath().contains(":muzzle-Assert"))
              .extracting(BuildTask::getPath)
              .containsExactly(
                  slice == 1
                      ? ":instrumentation:a:muzzle-AssertPass-fixture-library-1.0"
                      : ":instrumentation:c:muzzle-AssertPass-fixture-module-c-1.0");
          if (run == 2) {
            assertThat(result.getOutput()).contains("Reusing configuration cache");
          }
        }
        assertThat(requests)
            .anySatisfy(
                path ->
                    assertThat(path)
                        .contains(
                            slice == 1
                                ? "/library/maven-metadata.xml"
                                : "/module-c/maven-metadata.xml"));
        for (String child : asList("a", "b", "c", "d")) {
          if (!child.equals(slice == 1 ? "a" : "c")) {
            assertThat(project.resolve("instrumentation/" + child + "/build/muzzleRepo"))
                .doesNotExist();
          }
        }
      }
    }
  }

  @Test
  void assignedSlicesKeepEagerDiscoveryForMixedAndDirectAssertionSelectors() throws IOException {
    startRepository();
    for (String child : asList("b", "c", "d")) {
      publishLibrary("module-" + child);
    }
    for (boolean benchmark : asList(false, true)) {
      for (boolean mixed : asList(false, true)) {
        Path project = slicedFixture("fallback-" + benchmark + "-" + mixed, true);
        requests.clear();
        GradleRunner runner = runner(project, benchmark);
        List<String> arguments = new ArrayList<>(runner.getArguments());
        arguments.set(
            0, mixed ? ":instrumentation:muzzle1" : "muzzle-AssertPass-fixture-module-c-1.0");
        if (mixed) {
          arguments.add(":instrumentation:c:muzzle-AssertPass-fixture-module-c-1.0");
        }
        BuildResult result = runner.withArguments(arguments).build();
        assertThat(result.getTasks())
            .filteredOn(task -> task.getPath().contains(":muzzle-Assert"))
            .extracting(BuildTask::getPath)
            .contains(":instrumentation:c:muzzle-AssertPass-fixture-module-c-1.0")
            .hasSize(mixed ? 2 : 1);
        for (String module : asList("library", "module-b", "module-c", "module-d")) {
          assertThat(requests)
              .anySatisfy(path -> assertThat(path).contains("/" + module + "/maven-metadata.xml"));
        }
      }
    }
  }

  private void useOpenRange(Path project) throws IOException {
    Path build = project.resolve("build.gradle");
    Files.writeString(
        build, Files.readString(build).replace("versions.set('[1.0]')", "versions.set('[1.0,)')"));
  }

  private Path slicedFixture(String name) throws IOException {
    return slicedFixture(name, false);
  }

  private Path slicedFixture(String name, boolean assignSlices) throws IOException {
    Path project = temp.resolve(name);
    fixture(name + "/instrumentation/a", true, "library");
    for (String child : asList("b", "c", "d")) {
      fixture(name + "/instrumentation/" + child, true, "module-" + child);
    }
    Files.writeString(
        project.resolve("settings.gradle"),
        """
        rootProject.name = 'slice-fixture'
        include 'instrumentation', 'instrumentation:a', 'instrumentation:b', 'instrumentation:c', 'instrumentation:d'
        """);
    Files.writeString(
        project.resolve("build.gradle"),
        """
        plugins { id 'io.opentelemetry.instrumentation.muzzle-check' apply false }
        project(':instrumentation') {
          def slices = (1..%d).collect { tasks.register("muzzle${it}") }
          def counter = 0
          subprojects { child ->
            child.plugins.withId('io.opentelemetry.instrumentation.muzzle-check') {
              def sliceIndex = counter++ %% slices.size()
              %s
              slices[sliceIndex].configure { dependsOn(child.tasks.named('muzzle')) }
            }
          }
        }
        gradle.projectsEvaluated {
          if (!%s) {
            ['a', 'b', 'c', 'd'].each {
              assert file("instrumentation/${it}/build/muzzleRepo").isDirectory()
            }
          }
        }
        """
            .formatted(
                assignSlices ? 8 : 4,
                assignSlices ? "child.ext.otelMuzzleSlice = sliceIndex + 1" : "",
                assignSlices));
    return project;
  }

  private GradleRunner runner(Path project, boolean benchmark, String... arguments) {
    List<String> command =
        new ArrayList<>(
            asList(
                "muzzle",
                "--build-cache",
                "--configuration-cache",
                "--stacktrace",
                "--max-workers=2"));
    if (benchmark) {
      command.add("-PmuzzleBenchmarkSeed=1729");
      command.add("-PmuzzleBenchmarkReportDirectory=" + project.resolve("reports"));
    }
    command.addAll(asList(arguments));
    return GradleRunner.create()
        .withProjectDir(project.toFile())
        .withPluginClasspath()
        .withArguments(command);
  }

  private Path fixture(String name, boolean assertPass, String module) throws IOException {
    Path project = temp.resolve(name);
    Files.createDirectories(
        project.resolve("src/main/java/io/opentelemetry/javaagent/tooling/muzzle"));
    Files.createDirectories(project.resolve("src/main/resources"));
    Files.writeString(
        project.resolve("settings.gradle"),
        """
        rootProject.name = 'benchmark-fixture'
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
          }
        }
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
