/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.internal.logging;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ApplicationLoggerInstrumentationTest {

  @ParameterizedTest
  @ValueSource(classes = {TestApp.class, TestSpringApp.class})
  void shouldUseApplicationLogger(Class<?> mainClass) throws Exception {
    // gradle itself includes slf4j, and initializes it way before this method is executed
    // to remove the gradle interference, we're running the test apps in separate subprocesses
    List<String> logs = forkAndRun(mainClass.getName());

    assertThat(logs)
        .anyMatch(
            log ->
                log.startsWith(
                    "INFO io.opentelemetry.javaagent.tooling.VersionLogger :: opentelemetry-javaagent - version: "));
  }

  private static List<String> forkAndRun(String mainClassName) throws Exception {
    String javaPath =
        System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
    String javaagentPath = System.getProperty("otel.javaagent.testing.javaagent-jar-path");

    Process process =
        new ProcessBuilder(
                asList(
                    javaPath,
                    "-javaagent:" + javaagentPath,
                    "-cp",
                    System.getProperty("java.class.path"),
                    "-Dotel.sdk.disabled=true",
                    "-Dotel.javaagent.logging=application",
                    mainClassName))
            .redirectErrorStream(true)
            .start();
    InputStream stdout = process.getInputStream();

    CompletableFuture<List<String>> output =
        CompletableFuture.supplyAsync(
            () -> {
              try (BufferedReader reader =
                  new BufferedReader(new InputStreamReader(stdout, StandardCharsets.UTF_8))) {
                List<String> lines = new ArrayList<>();
                String line;
                while ((line = reader.readLine()) != null) {
                  lines.add(line);
                }
                return lines;
              } catch (IOException e) {
                throw new AssertionError("Unexpected error while retrieving subprocess output", e);
              }
            });

    process.waitFor(10, TimeUnit.SECONDS);
    return output.join();
  }
}
