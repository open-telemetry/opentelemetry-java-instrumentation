/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest;

import static io.opentelemetry.sdk.testing.assertj.TracesAssert.assertThat;

import io.opentelemetry.semconv.ServiceAttributes;
import io.opentelemetry.semconv.incubating.TelemetryIncubatingAttributes;
import java.time.Duration;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import org.junit.jupiter.api.condition.DisabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisabledIf("io.opentelemetry.smoketest.TestContainerManager#useWindowsContainers")
class QuarkusSmokeTest extends JavaSmokeTest {
  @Override
  protected String getTargetImage(String jdk) {
    return "ghcr.io/open-telemetry/opentelemetry-java-instrumentation/smoke-test-quarkus:jdk"
        + jdk
        + "-20250915.17728045126";
  }

  @Override
  protected TargetWaitStrategy getWaitStrategy() {
    return new TargetWaitStrategy.Log(Duration.ofMinutes(1), ".*Listening on.*");
  }

  @Override
  protected boolean getSetServiceName() {
    return false;
  }

  @ParameterizedTest
  @ValueSource(ints = {17, 21, 23}) // Quarkus 3.7+ requires Java 17+
  void quarkusSmokeTest(int jdk) throws Exception {
    runTarget(
        jdk,
        output -> {
          String currentAgentVersion;
          try (JarFile agentJar = new JarFile(agentPath)) {
            currentAgentVersion =
                agentJar
                    .getManifest()
                    .getMainAttributes()
                    .getValue(Attributes.Name.IMPLEMENTATION_VERSION);
          }

          client().get("/hello").aggregate().join();

          assertThat(waitForTraces())
              .hasTracesSatisfyingExactly(
                  trace ->
                      trace.hasSpansSatisfyingExactly(
                          span ->
                              span.hasName("GET /hello")
                                  .hasResourceSatisfying(
                                      resource -> {
                                        resource
                                            .hasAttribute(
                                                TelemetryIncubatingAttributes
                                                    .TELEMETRY_DISTRO_VERSION,
                                                currentAgentVersion)
                                            .hasAttribute(
                                                ServiceAttributes.SERVICE_NAME, "quarkus");
                                      })));
        });
  }
}
