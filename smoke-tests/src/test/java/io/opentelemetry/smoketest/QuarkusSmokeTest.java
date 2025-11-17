/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest;

import io.opentelemetry.semconv.ServiceAttributes;
import io.opentelemetry.semconv.incubating.TelemetryIncubatingAttributes;
import java.time.Duration;
import org.junit.jupiter.api.condition.DisabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisabledIf("io.opentelemetry.smoketest.TestContainerManager#useWindowsContainers")
class QuarkusSmokeTest extends AbstractSmokeTest<Integer> {

  @Override
  protected void configure(SmokeTestOptions<Integer> options) {
    options
        .image(
            jdk ->
                String.format(
                    "ghcr.io/open-telemetry/opentelemetry-java-instrumentation/smoke-test-quarkus:jdk%s-%s",
                    jdk, ImageVersions.QUARKUS_VERSION))
        .waitStrategy(new TargetWaitStrategy.Log(Duration.ofMinutes(1), ".*Listening on.*"))
        .setServiceName(false);
  }

  @ParameterizedTest
  @ValueSource(ints = {17, 21, 25}) // Quarkus 3.7+ requires Java 17+
  void quarkusSmokeTest(int jdk) {
    start(jdk);

    client().get("/hello").aggregate().join();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("GET /hello")
                        .hasResourceSatisfying(
                            resource -> {
                              resource
                                  .hasAttribute(
                                      TelemetryIncubatingAttributes.TELEMETRY_DISTRO_VERSION,
                                      getAgentVersion())
                                  .hasAttribute(ServiceAttributes.SERVICE_NAME, "quarkus");
                            })));
  }
}
