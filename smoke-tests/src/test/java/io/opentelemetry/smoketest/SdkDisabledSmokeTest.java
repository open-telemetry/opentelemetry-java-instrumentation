/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;

import java.util.function.Consumer;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import org.junit.jupiter.api.condition.DisabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.containers.output.OutputFrame;

@DisabledIf("io.opentelemetry.smoketest.TestContainerManager#useWindowsContainers")
class SdkDisabledSmokeTest extends JavaSmokeTest {

  public SdkDisabledSmokeTest() {
    super(SmokeTestTarget.springBoot("20211213.1570880324").env("OTEL_SDK_DISABLED", "true"));
  }

  @ParameterizedTest
  @ValueSource(ints = {8, 11, 17})
  void noopSdkSmokeTest(int jdk) throws Exception {
    Consumer<OutputFrame> output = startTarget(jdk);
    String currentAgentVersion =
        new JarFile(agentPath)
            .getManifest()
            .getMainAttributes()
            .get(Attributes.Name.IMPLEMENTATION_VERSION)
            .toString();

    assertThat(client().get("/greeting").aggregate().join().contentUtf8()).isEqualTo("Hi!");
    assertThat(testing.spans()).isEmpty();
    assertVersionLogged(output, currentAgentVersion);
    assertThat(testing.spans()).isEmpty();
  }
}
