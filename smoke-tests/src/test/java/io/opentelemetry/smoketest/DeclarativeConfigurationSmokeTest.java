/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.ServiceAttributes.SERVICE_NAME;
import static io.opentelemetry.semconv.incubating.ContainerIncubatingAttributes.CONTAINER_ID;
import static io.opentelemetry.semconv.incubating.HostIncubatingAttributes.HOST_NAME;
import static io.opentelemetry.semconv.incubating.ProcessIncubatingAttributes.PROCESS_EXECUTABLE_PATH;
import static io.opentelemetry.semconv.incubating.TelemetryIncubatingAttributes.TELEMETRY_DISTRO_NAME;

import org.junit.jupiter.api.condition.DisabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisabledIf("io.opentelemetry.smoketest.TestContainerManager#useWindowsContainers")
class DeclarativeConfigurationSmokeTest extends AbstractSmokeTest<Integer> {

  @Override
  protected void configure(SmokeTestOptions<Integer> options) {
    options
        .springBoot()
        .env("OTEL_EXPERIMENTAL_CONFIG_FILE", "declarative-config.yaml")
        .extraResources(ResourceMapping.of("declarative-config.yaml", "/declarative-config.yaml"));
  }

  @ParameterizedTest
  @ValueSource(ints = {8, 11, 17, 21, 25})
  void springBootSmokeTest(int jdk) {
    start(jdk);

    client().get("/greeting").aggregate().join();

    // There is one span (io.opentelemetry.opentelemetry-instrumentation-annotations-1.16 is
    // not used, because default_enabled=false)
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasResourceSatisfying(
                        resource ->
                            resource
                                .hasAttribute(SERVICE_NAME, "declarative-config-smoke-test")
                                .hasAttribute(satisfies(CONTAINER_ID, v -> v.isNotBlank()))
                                .hasAttribute(
                                    satisfies(PROCESS_EXECUTABLE_PATH, v -> v.isNotBlank()))
                                .hasAttribute(satisfies(HOST_NAME, v -> v.isNotBlank()))
                                .hasAttribute(TELEMETRY_DISTRO_NAME, "opentelemetry-javaagent"))));
  }
}
