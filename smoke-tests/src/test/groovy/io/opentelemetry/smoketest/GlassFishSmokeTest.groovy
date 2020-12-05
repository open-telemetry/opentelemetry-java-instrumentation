/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest


import java.time.Duration
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.containers.wait.strategy.WaitStrategy

class GlassFishSmokeTest extends AppServerTest {

  protected String getTargetImage(int jdk, String serverVersion) {
    "ghcr.io/open-telemetry/java-test-containers:payara-${serverVersion}-jdk$jdk"
  }

  @Override
  protected Map<String, String> getExtraEnv() {
    return ["HZ_PHONE_HOME_ENABLED": "false"]
  }

  @Override
  protected WaitStrategy getWaitStrategy() {
    return Wait
      .forLogMessage(".*app was successfully deployed.*", 1)
      .withStartupTimeout(Duration.ofMinutes(3))
  }

  @Override
  List<List<Object>> getTestParams() {
    return [
      ["5.2020.6", 8],
      ["5.2020.6-jdk11", 11]
    ]
  }
}
