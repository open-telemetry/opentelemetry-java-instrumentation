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
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MuzzleSliceFunctionalTest {
  @TempDir Path temp;

  @Test
  void selectedSliceExecutesAssertionsWithoutDiscoveringOtherProjects() throws IOException {
    Path artifact = temp.resolve("repository/fixture/library");
    Files.createDirectories(artifact.resolve("1.0"));
    Files.writeString(
        artifact.resolve("maven-metadata.xml"),
        "<metadata><versioning><versions><version>1.0</version></versions></versioning></metadata>");
    Files.writeString(
        artifact.resolve("1.0/library-1.0.pom"),
        """
        <project><modelVersion>4.0.0</modelVersion><groupId>fixture</groupId>
        <artifactId>library</artifactId><version>1.0</version></project>
        """);
    try (JarOutputStream jar =
        new JarOutputStream(Files.newOutputStream(artifact.resolve("1.0/library-1.0.jar")))) {
      jar.putNextEntry(new JarEntry("probe.txt"));
      jar.write("library".getBytes(UTF_8));
      jar.closeEntry();
    }

    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/",
        exchange -> {
          try {
            Path file = temp.resolve("repository" + exchange.getRequestURI().getPath()).normalize();
            if (!Files.isRegularFile(file)) {
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
    try {
      Files.writeString(
          temp.resolve("settings.gradle"),
          "include 'instrumentation', 'instrumentation:a', 'instrumentation:b'");
      Files.writeString(
          temp.resolve("build.gradle"),
          """
          plugins { id 'io.opentelemetry.instrumentation.muzzle-check' apply false }
          project(':instrumentation') {
            def slices = (1..8).collect { tasks.register("muzzle${it}") }
            def counter = 0
            subprojects { child ->
              child.plugins.withId('io.opentelemetry.instrumentation.muzzle-check') {
                def sliceIndex = counter++ % slices.size()
                child.ext.otelMuzzleSlice = sliceIndex + 1
                slices[sliceIndex].configure { dependsOn(child.tasks.named('muzzle')) }
              }
            }
          }
          """);
      for (String child : asList("a", "b")) {
        Path module = temp.resolve("instrumentation/" + child);
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
                module.set('library')
                versions.set('[1.0]')
              }
            }
            """
                .formatted(server.getAddress().getPort()));
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
                return Map.of("fixture", List.of());
              }
            }
            """);
      }
      BuildResult result =
          GradleRunner.create()
              .withProjectDir(temp.toFile())
              .withPluginClasspath()
              .withArguments(":instrumentation:muzzle1", "--configuration-cache", "--max-workers=2")
              .build();
      assertThat(
              result.task(":instrumentation:a:muzzle-AssertPass-fixture-library-1.0").getOutcome())
          .isEqualTo(TaskOutcome.SUCCESS);
      assertThat(
              temp.resolve(
                  "instrumentation/a/build/muzzleRepo/fixture/library/maven-metadata-maven.xml"))
          .isRegularFile();
      assertThat(temp.resolve("instrumentation/b/build/muzzleRepo")).doesNotExist();
    } finally {
      server.stop(0);
    }
  }
}
