/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest;

import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.testcontainers.containers.output.OutputFrame;
import org.testcontainers.containers.output.ToStringConsumer;

public class SmokeTestOutput {

  private static final Pattern TRACE_ID_PATTERN =
      Pattern.compile(".*trace_id=(?<traceId>[a-zA-Z0-9]+).*");
  private final AbstractSmokeTest<?> smokeTest;
  private final Consumer<OutputFrame> output;

  public SmokeTestOutput(AbstractSmokeTest<?> smokeTest, Consumer<OutputFrame> output) {
    this.smokeTest = smokeTest;
    this.output = output;
  }

  public static Stream<String> findTraceId(String log) {
    var m = TRACE_ID_PATTERN.matcher(log);
    return m.matches() ? Stream.of(m.group("traceId")) : Stream.empty();
  }

  public void assertAgentVersionLogged() {
    String version = smokeTest.getAgentVersion();
    assertThat(
            logLines().anyMatch(l -> l.contains("opentelemetry-javaagent - version: " + version)))
        .isTrue();
  }

  public Set<String> getLoggedTraceIds() {
    return logLines().flatMap(SmokeTestOutput::findTraceId).collect(toSet());
  }

  public Stream<String> logLines() {
    return ((ToStringConsumer) output).toUtf8String().lines();
  }
}
