/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.muzzle;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MuzzleTaskSelectionTest {
  private static final List<String> MODULES = List.of("alpha", "bravo", "charlie", "delta");

  @TempDir Path directory;

  private final Set<String> contactedModules = ConcurrentHashMap.newKeySet();
  private HttpServer repository;

  @BeforeEach
  void setUp() throws IOException {
    repository = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    repository.createContext(
        "/",
        exchange -> {
          String path = exchange.getRequestURI().getPath();
          contactedModules.add(path.split("/")[2]);
          if (path.endsWith("/maven-metadata.xml")) {
            byte[] metadata =
                ("<metadata><groupId>fixture</groupId><artifactId>"
                        + path.split("/")[2]
                        + "</artifactId><versioning><versions>"
                        + "<version>1.0</version><version>2.0</version><version>3.0</version>"
                        + "<version>4.0</version><version>5.0</version>"
                        + "</versions></versioning></metadata>")
                    .getBytes(UTF_8);
            exchange.sendResponseHeaders(200, metadata.length);
            exchange.getResponseBody().write(metadata);
          } else {
            exchange.sendResponseHeaders(404, -1);
          }
          exchange.close();
        });
    repository.start();
    write("settings.gradle", "rootProject.name = 'muzzle-selection'\n");
    write(
        "build.gradle",
        """
        gradle.projectsEvaluated {
          allprojects.each { p ->
            def assertions = p.configurations.names.findAll { it.startsWith('muzzle-Assert') }.sort()
            println "MODEL:${p.path}=${assertions.join(',')}"
          }
        }
        """);
    // Exercise the real aggregate script, substituting only its unrelated convention plugin.
    write(
        "instrumentation/build.gradle.kts",
        Files.readString(Path.of(System.getProperty("muzzle.aggregateBuildScript")))
            .replace("id(\"otel.java-conventions\")", "java"));
    for (String module : MODULES) {
      include("instrumentation:" + module, moduleBuild(module));
    }
    write(
        ".github/config/latest-dep-versions.json",
        MODULES.stream()
            .map(module -> "\"fixture:" + module + "#+\":\"4.0\"")
            .collect(Collectors.joining(",", "{", "}")));
  }

  @AfterEach
  void tearDown() {
    repository.stop(0);
  }

  @Test
  void partitionRequestOnlyEnumeratesAndSchedulesItsMembers() {
    assertSelected(run("instrumentation:muzzle2"), Set.of("bravo"));
  }

  @Test
  void explicitModuleRequestOnlyEnumeratesAndSchedulesThatModule() {
    assertSelected(run(":instrumentation:alpha:muzzle"), Set.of("alpha"));
  }

  @Test
  void multiplePartitionAndModuleRequestsSelectTheirUnion() {
    assertSelected(
        run(
            ":instrumentation:muzzle2",
            "instrumentation:muzzle3",
            ":instrumentation:alpha:muzzle"),
        Set.of("alpha", "bravo", "charlie"));
  }

  private void assertSelected(BuildResult result, Set<String> selectedModules) {
    assertThat(contactedModules).containsExactlyInAnyOrderElementsOf(selectedModules);
    for (String selected : selectedModules) {
      String task = ":instrumentation:" + selected + ":muzzle";
      assertThat(result.getOutput())
          .contains(
              task + " SKIPPED",
              task + "-AssertPass-fixture-" + selected + "-2.0 SKIPPED",
              task + "-AssertPass-fixture-" + selected + "-3.0 SKIPPED",
              task + "-AssertFail-fixture-" + selected + "-1.0 SKIPPED",
              task + "-AssertFail-fixture-" + selected + "-4.0 SKIPPED",
              "MODEL::instrumentation:"
                  + selected
                  + "=muzzle-AssertFail-fixture-"
                  + selected
                  + "-1.0,muzzle-AssertFail-fixture-"
                  + selected
                  + "-4.0,muzzle-AssertPass-fixture-"
                  + selected
                  + "-2.0,muzzle-AssertPass-fixture-"
                  + selected
                  + "-3.0\n");
    }
    for (String module : MODULES) {
      if (!selectedModules.contains(module)) {
        assertThat(result.getOutput())
            .contains("MODEL::instrumentation:" + module + "=\n")
            .doesNotContain(":instrumentation:" + module + ":muzzle");
      }
    }
  }

  private String moduleBuild(String module) {
    return """
    plugins { id 'io.opentelemetry.instrumentation.muzzle-check' }
    repositories { maven { url = 'http://127.0.0.1:%d'; allowInsecureProtocol = true } }
    muzzle {
      pass {
        group = 'fixture'
        module = '%s'
        versions = '[2.0,4.0)'
        assertInverse = true
      }
    }
    """
        .formatted(repository.getAddress().getPort(), module);
  }

  private BuildResult run(String... arguments) {
    return runner(arguments).build();
  }

  private GradleRunner runner(String... arguments) {
    List<String> args = new ArrayList<>(List.of(arguments));
    args.addAll(List.of("--dry-run", "--stacktrace", "--no-configuration-cache"));
    return GradleRunner.create()
        .withProjectDir(directory.toFile())
        .withPluginClasspath()
        .withArguments(args);
  }

  private void include(String project, String build) throws IOException {
    Files.writeString(
        directory.resolve("settings.gradle"),
        "include '" + project + "'\n",
        java.nio.file.StandardOpenOption.APPEND);
    write(project.replace(':', '/') + "/build.gradle", build);
  }

  private void write(String path, String content) throws IOException {
    Path file = directory.resolve(path);
    Files.createDirectories(file.getParent());
    Files.writeString(file, content);
  }
}
