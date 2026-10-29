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
import java.util.Arrays;
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
  private static final List<String> MODULES =
      List.of("alpha", "bravo", "charlie", "delta", "echo", "foxtrot", "golf", "hotel");

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
        tasks.register('muzzleReport')
        gradle.projectsEvaluated {
          allprojects.each { p ->
            def assertions = p.configurations.names.findAll { it.startsWith('muzzle-Assert') }.sort()
            println "MODEL:${p.path}=${assertions.join(',')}"
            if (p.extensions.extraProperties.has('otel.muzzle.aggregateTaskPath')) {
              println "PARTITION:${p.path}=${p.extensions.extraProperties.get('otel.muzzle.aggregateTaskPath')}"
            }
          }
        }
        """);
    // Exercise the real aggregate script, substituting only its unrelated convention plugin.
    write(
        "instrumentation/build.gradle.kts",
        Files.readString(Path.of(System.getProperty("muzzle.aggregateBuildScript")))
            .replace("id(\"otel.java-conventions\")", "java"));
    // A project without the Muzzle plugin must not consume a partition slot.
    include("instrumentation:aardvark", "");
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
  void partitionsUseTheExistingAssignmentAndOnlyEnumerateTheirMembers() {
    BuildResult result = run("instrumentation:muzzle2");
    assertSelected(result, "bravo", "foxtrot");
    assertScheduled(result, "bravo", "foxtrot");
    for (int i = 0; i < MODULES.size(); i++) {
      assertThat(result.getOutput())
          .contains(
              "PARTITION::instrumentation:"
                  + MODULES.get(i)
                  + "=:instrumentation:muzzle"
                  + (i % 4 + 1));
    }
  }

  @Test
  void multiplePartitionsAndExplicitModulesFormAUnion() {
    BuildResult result =
        run(":instrumentation:muzzle1", "instrumentation:muzzle3", ":instrumentation:bravo:muzzle");
    assertSelected(result, "alpha", "bravo", "charlie", "echo", "golf");
    assertScheduled(result, "alpha", "bravo", "charlie", "echo", "golf");
  }

  @Test
  void directAssertionsSelectTheirProjectAndKeepTheFinalizerOrder() {
    BuildResult result = run(":instrumentation:bravo:muzzle-AssertPass-fixture-bravo-2.0");
    assertSelected(result, "bravo");
    assertThat(result.getOutput())
        .containsSubsequence(
            ":instrumentation:bravo:muzzle-AssertPass-fixture-bravo-2.0 SKIPPED",
            ":instrumentation:bravo:muzzle-AssertFail-fixture-bravo-1.0 SKIPPED",
            ":instrumentation:bravo:muzzle-AssertFail-fixture-bravo-4.0 SKIPPED",
            ":instrumentation:bravo:muzzle-AssertFail-fixture-bravo-1.0explicit SKIPPED",
            ":instrumentation:bravo:muzzle-AssertPass-core-jdk SKIPPED");
  }

  @Test
  void relativeModuleInvocationWorks() {
    assertSelected(run("-p", "instrumentation/bravo", "muzzle"), "bravo");
  }

  @Test
  void ambiguousProjectSelectorsDoNotDropChecks() {
    assertSelected(run(":inst:muzzle1"), MODULES.toArray(new String[0]));
  }

  @Test
  void abbreviatedAssertionSelectorsDoNotDropChecks() {
    assertSelected(
        run(":instrumentation:bravo:muzzle-AP-core-jdk"), MODULES.toArray(new String[0]));
  }

  @Test
  void unrelatedRequestsDoNotEnumerateVersions() {
    assertSelected(run("help", "muzzleReport"));
  }

  @Test
  void externalProjectsNeedNoPartitionMetadata() throws IOException {
    include("external", moduleBuild("external"));
    // Fixed ranges still resolve without this repository's pinned catalog.
    Files.delete(directory.resolve(".github/config/latest-dep-versions.json"));
    BuildResult result = run("external:muzzle");
    assertModels(result);
    assertThat(contactedModules).containsExactly("external");
    assertThat(result.getOutput())
        .contains("MODEL::external=muzzle-AssertFail-fixture-external-1.0");
  }

  @Test
  void missingPinnedEntriesStillFailOnlyWhenSelected() throws IOException {
    Path pinnedVersions = directory.resolve(".github/config/latest-dep-versions.json");
    write(
        ".github/config/latest-dep-versions.json",
        Files.readString(pinnedVersions).replace("\"fixture:alpha#+\":\"4.0\",", ""));
    assertSelected(run(":instrumentation:muzzle2"), "bravo", "foxtrot");
    contactedModules.clear();
    BuildResult result = runner(":instrumentation:alpha:muzzle").buildAndFail();
    assertThat(result.getOutput())
        .contains("Pinned version missing for muzzle artifact \"fixture:alpha#+\"");
    assertThat(contactedModules).isEmpty();
  }

  private void assertSelected(BuildResult result, String... selected) {
    assertThat(contactedModules).containsExactlyInAnyOrder(selected);
    assertModels(result, selected);
  }

  private static void assertScheduled(BuildResult result, String... selected) {
    Set<String> expected = Set.of(selected);
    for (String module : MODULES) {
      String task = ":instrumentation:" + module + ":muzzle";
      if (expected.contains(module)) {
        assertThat(result.getOutput())
            .containsSubsequence(
                task + " SKIPPED",
                task + "-AssertPass-fixture-" + module + "-2.0 SKIPPED",
                task + "-AssertFail-fixture-" + module + "-1.0 SKIPPED",
                task + "-AssertFail-fixture-" + module + "-4.0 SKIPPED",
                task + "-AssertFail-fixture-" + module + "-1.0explicit SKIPPED",
                task + "-AssertPass-core-jdk SKIPPED");
      } else {
        assertThat(result.getOutput()).doesNotContain(task + " SKIPPED");
      }
    }
  }

  private void assertModels(BuildResult result, String... selected) {
    Set<String> expected = Set.of(selected);
    for (String module : MODULES) {
      String model = "MODEL::instrumentation:" + module + "=";
      if (expected.contains(module)) {
        assertThat(result.getOutput())
            .contains(
                model
                    + "muzzle-AssertFail-fixture-"
                    + module
                    + "-1.0,muzzle-AssertFail-fixture-"
                    + module
                    + "-1.0explicit,muzzle-AssertFail-fixture-"
                    + module
                    + "-4.0,muzzle-AssertPass-core-jdk,muzzle-AssertPass-fixture-"
                    + module
                    + "-2.0\n");
      } else {
        assertThat(result.getOutput()).contains(model + "\n");
        assertThat(directory.resolve("instrumentation/" + module + "/build/muzzleRepo"))
            .doesNotExist();
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
        skip '3.0'
        excludeDependency 'unused:dependency'
        excludeInstrumentationName 'unused-name'
        excludeInstrumentationModule 'unused.Module'
      }
      fail {
        name = 'explicit'
        group = 'fixture'
        module = '%s'
        versions = '[1.0]'
      }
      pass { coreJdk = true }
    }
    """
        .formatted(repository.getAddress().getPort(), module, module);
  }

  private BuildResult run(String... arguments) {
    return runner(arguments).build();
  }

  private GradleRunner runner(String... arguments) {
    List<String> args = new ArrayList<>(Arrays.asList(arguments));
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
