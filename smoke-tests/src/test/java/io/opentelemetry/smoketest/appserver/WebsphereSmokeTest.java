/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest.appserver;

import io.opentelemetry.smoketest.SmokeTestInstrumentationExtension;
import io.opentelemetry.smoketest.TargetWaitStrategy;
import java.time.Duration;
import org.junit.jupiter.api.extension.RegisterExtension;

public abstract class WebsphereSmokeTest extends AppServerTest {

  @RegisterExtension
  static final SmokeTestInstrumentationExtension<AppServerImage> testing =
      builder(
              "ghcr.io/open-telemetry/opentelemetry-java-instrumentation/smoke-test-servlet-websphere")
          .waitStrategy(
              new TargetWaitStrategy.Log(
                  Duration.ofMinutes(3), ".*Server server1 open for e-business.*"))
          .build();

  @Override
  protected SmokeTestInstrumentationExtension<AppServerImage> testing() {
    return testing;
  }

  @Override
  protected String getSpanName(String path) {
    if ("/app/hello.txt".equals(path) || "/app/file-that-does-not-exist".equals(path)) {
      return "GET";
    }
    return super.getSpanName(path);
  }

  @Override
  protected boolean testRequestOutsideDeployedApp() {
    return false;
  }

  @AppServer(version = "8.5.5.22", jdk = "8-openj9")
  public static class Websphere8Jdk8Openj9 extends WebsphereSmokeTest {}

  @AppServer(version = "9.0.5.14", jdk = "8-openj9")
  public static class Websphere9Jdk8Openj9 extends WebsphereSmokeTest {}
}
