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
import org.junit.jupiter.api.condition.DisabledIf;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisabledIf("io.opentelemetry.smoketest.TestContainerManager#useWindowsContainers")
class DeclarativeConfigurationSmokeTest {

  @RegisterExtension
  static final SmokeTestInstrumentationExtension testing =
      SmokeTestInstrumentationExtension.springBoot("20241021.11448062567")
          .env("OTEL_EXPERIMENTAL_CONFIG_FILE", "declarative-config.yaml")
          .extraResources(ResourceMapping.of("declarative-config.yaml", "/declarative-config.yaml"))
          .build();

  @ParameterizedTest
  @ValueSource(ints = {8, 11, 17})
  void springBootSmokeTest(int jdk) {
    testing.start(jdk);

    testing.client().get("/greeting").aggregate().join();

    // There is one span (io.opentelemetry.opentelemetry-instrumentation-annotations-1.16 is
    // not used, because instrumentation_mode=none)
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
