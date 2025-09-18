/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;

import io.opentelemetry.semconv.ServiceAttributes;
import io.opentelemetry.semconv.incubating.ContainerIncubatingAttributes;
import io.opentelemetry.semconv.incubating.HostIncubatingAttributes;
import io.opentelemetry.semconv.incubating.ProcessIncubatingAttributes;
import io.opentelemetry.semconv.incubating.TelemetryIncubatingAttributes;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.condition.DisabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisabledIf("io.opentelemetry.smoketest.TestContainerManager#useWindowsContainers")
class DeclarativeConfigurationSmokeTest extends JavaSmokeTest {
  @Override
  protected String getTargetImage(String jdk) {
    return "ghcr.io/open-telemetry/opentelemetry-java-instrumentation/smoke-test-spring-boot:jdk"
        + jdk
        + "-20241021.11448062567";
  }

  @Override
  protected List<ResourceMapping> getExtraResources() {
    return List.of(ResourceMapping.of("declarative-config.yaml", "/declarative-config.yaml"));
  }

  @Override
  protected Map<String, String> getExtraEnv() {
    return Map.of("OTEL_EXPERIMENTAL_CONFIG_FILE", "declarative-config.yaml");
  }

  @Override
  protected TargetWaitStrategy getWaitStrategy() {
    return new TargetWaitStrategy.Log(
        Duration.ofMinutes(1), ".*Started SpringbootApplication in.*");
  }

  @ParameterizedTest
  @ValueSource(ints = {8, 11, 17})
  void springBootSmokeTest(int jdk) {
    startTarget(jdk);

    client().get("/greeting").aggregate().join();

    // There is one span (io.opentelemetry.opentelemetry-instrumentation-annotations-1.16 is
    // not used,
    // because instrumentation_mode=none)
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasResourceSatisfying(
                        resource ->
                            resource
                                .hasAttribute(
                                    ServiceAttributes.SERVICE_NAME, "declarative-config-smoke-test")
                                .hasAttribute(
                                    satisfies(
                                        ContainerIncubatingAttributes.CONTAINER_ID,
                                        v -> v.isNotBlank()))
                                .hasAttribute(
                                    satisfies(
                                        ProcessIncubatingAttributes.PROCESS_EXECUTABLE_PATH,
                                        v -> v.isNotBlank()))
                                .hasAttribute(
                                    satisfies(
                                        HostIncubatingAttributes.HOST_NAME, v -> v.isNotBlank()))
                                .hasAttribute(
                                    TelemetryIncubatingAttributes.TELEMETRY_DISTRO_NAME,
                                    "opentelemetry-javaagent"))));
  }
}
