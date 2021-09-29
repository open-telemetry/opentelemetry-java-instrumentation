/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest

import java.time.Duration

@AppServer(version = "5.2020.6", jdk = "8")
@AppServer(version = "5.2020.6", jdk = "8-openj9")
@AppServer(version = "5.2020.6", jdk = "11")
@AppServer(version = "5.2020.6", jdk = "11-openj9")
class GlassFishSmokeTest extends AppServerTest {

  protected String getTargetImagePrefix() {
    "ghcr.io/open-telemetry/opentelemetry-java-instrumentation/smoke-test-servlet-payara"
  }

  @Override
  protected Map<String, String> getExtraEnv() {
    return ["HZ_PHONE_HOME_ENABLED": "false"]
  }

  @Override
  protected String getJvmArgsEnvVarName() {
    return "JVM_ARGS"
  }

  @Override
  protected TargetWaitStrategy getWaitStrategy() {
    return new TargetWaitStrategy.Log(Duration.ofMinutes(3), ".*(app was successfully deployed|deployed with name app).*")
  }

  @Override
  protected String getSpanName(String path) {
    switch (path) {
      case "/this-is-definitely-not-there-but-there-should-be-a-trace-nevertheless":
        return "/*"
    }
    return super.getSpanName(path)
  }

  @Override
  boolean testRequestWebInfWebXml() {
    false
  }
}
