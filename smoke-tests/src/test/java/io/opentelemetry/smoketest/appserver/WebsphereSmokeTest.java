/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest.appserver;

import io.opentelemetry.smoketest.SmokeTestOptions;
import io.opentelemetry.smoketest.TargetWaitStrategy;
import java.time.Duration;

abstract class WebsphereSmokeTest extends AppServerTest {

  @Override
  protected void configure(SmokeTestOptions<AppServerImage> options) {
    options
        .image(
            appServerImage(
                "ghcr.io/open-telemetry/opentelemetry-java-instrumentation/smoke-test-servlet-websphere"))
        .waitStrategy(
            new TargetWaitStrategy.Log(
                Duration.ofMinutes(3), ".*Server server1 open for e-business.*"));
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
  static class Websphere8Jdk8Openj9 extends WebsphereSmokeTest {}

  @AppServer(version = "9.0.5.14", jdk = "8-openj9")
  static class Websphere9Jdk8Openj9 extends WebsphereSmokeTest {}
}
