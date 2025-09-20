/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;

import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import org.junit.jupiter.api.condition.DisabledIf;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisabledIf("io.opentelemetry.smoketest.TestContainerManager#useWindowsContainers")
class SdkDisabledSmokeTest {

  @RegisterExtension
  static final SmokeTestTarget target =
      SmokeTestTarget.springBoot("20211213.1570880324").env("OTEL_SDK_DISABLED", "true").build();

  static final InstrumentationExtension testing = target.testing();

  @ParameterizedTest
  @ValueSource(ints = {8, 11, 17})
  void noopSdkSmokeTest(int jdk) throws Exception {
    SmokeTestOutput output = target.start(jdk);
    String currentAgentVersion =
        new JarFile(target.getAgentPath())
            .getManifest()
            .getMainAttributes()
            .get(Attributes.Name.IMPLEMENTATION_VERSION)
            .toString();

    assertThat(target.client().get("/greeting").aggregate().join().contentUtf8()).isEqualTo("Hi!");
    assertThat(testing.spans()).isEmpty();
    output.assertVersionLogged(currentAgentVersion);
    assertThat(testing.spans()).isEmpty();
  }
}
