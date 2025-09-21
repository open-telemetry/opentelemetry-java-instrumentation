/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest;

import io.opentelemetry.semconv.ServiceAttributes;
import io.opentelemetry.semconv.incubating.TelemetryIncubatingAttributes;
import java.time.Duration;
import org.junit.jupiter.api.condition.DisabledIf;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisabledIf("io.opentelemetry.smoketest.TestContainerManager#useWindowsContainers")
class QuarkusSmokeTest {

  @RegisterExtension
  static final SmokeTestInstrumentationExtension<Integer> testing =
      SmokeTestInstrumentationExtension.<Integer>builder(
              jdk ->
                  String.format(
                      "ghcr.io/open-telemetry/opentelemetry-java-instrumentation/smoke-test-quarkus:jdk%s-20250915.17728045126",
                      jdk))
          .waitStrategy(new TargetWaitStrategy.Log(Duration.ofMinutes(1), ".*Listening on.*"))
          .setServiceName(false)
          .build();

  @ParameterizedTest
  @ValueSource(ints = {17, 21, 23}) // Quarkus 3.7+ requires Java 17+
  void quarkusSmokeTest(int jdk) throws Exception {
    testing.start(jdk);

    testing.client().get("/hello").aggregate().join();

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
                                      testing.getAgentVersion())
                                  .hasAttribute(ServiceAttributes.SERVICE_NAME, "quarkus");
                            })));
  }
}
