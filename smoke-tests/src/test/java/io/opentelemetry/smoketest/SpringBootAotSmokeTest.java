/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;

@DisabledIf("io.opentelemetry.smoketest.TestContainerManager#useWindowsContainers")
class SpringBootAotSmokeTest extends AbstractSpringBootSmokeTest {

  @Override
  protected void configure(SmokeTestOptions<Integer> options) {
    super.configure(options);
    options
        .extraResources(ResourceMapping.of("spring-boot-aot/start.sh", "/spring-boot-aot.sh"))
        .logOutput(false)
        .waitStrategy(
            new TargetWaitStrategy.Log(
                Duration.ofMinutes(3), ".*Started SpringbootApplication in.*"))
        .entrypoint(
            "/bin/bash",
            "-c",
            "sed -i 's/\\r$//' /spring-boot-aot.sh && exec /bin/bash /spring-boot-aot.sh");
  }

  @Test
  void springBootAotSmokeTest() {
    SmokeTestOutput output = start(25);

    List<String> logLines = output.logLines().collect(toList());
    int agentStarted = indexOf(logLines, "opentelemetry-javaagent - version: " + getAgentVersion());
    int applicationLoadedFromCache =
        indexOf(
            logLines,
            "io.opentelemetry.smoketest.springboot.SpringbootApplication source: shared objects file");

    assertThat(indexOf(logLines, "Opened AOT cache ")).isNotNegative();
    assertThat(indexOf(logLines, "Using AOT-linked classes: true")).isNotNegative();
    assertThat(agentStarted).isNotNegative();
    assertThat(applicationLoadedFromCache).isNotNegative();
    assertThat(logLines)
        .noneMatch(
            line ->
                line.contains(
                    "Instrumentation.appendToBootstrapClassLoaderSearch has been called"));

    assertSpringBootTelemetry(output);
  }

  private static int indexOf(List<String> lines, String text) {
    for (int i = 0; i < lines.size(); i++) {
      if (lines.get(i).contains(text)) {
        return i;
      }
    }
    return -1;
  }
}
