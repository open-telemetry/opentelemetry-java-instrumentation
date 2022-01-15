/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest

import java.time.Duration

abstract class WebsphereSmokeTest extends AppServerTest {

  protected String getTargetImagePrefix() {
    "ghcr.io/open-telemetry/opentelemetry-java-instrumentation/smoke-test-servlet-websphere"
  }

  @Override
  protected TargetWaitStrategy getWaitStrategy() {
    return new TargetWaitStrategy.Log(Duration.ofMinutes(3), ".*Server server1 open for e-business.*")
  }

  @Override
  protected String getSpanName(String path) {
    switch (path) {
      case "/app/hello.txt":
      case "/app/file-that-does-not-exist":
        return "HTTP GET"
    }
    return super.getSpanName(path)
  }

  @Override
  boolean testRequestOutsideDeployedApp() {
    false
  }
}

@AppServer(version = "8.5.5.19", jdk = "8-openj9")
class Websphere8Jdk8Openj9 extends WebsphereSmokeTest {
}

@AppServer(version = "9.0.5.9", jdk = "8-openj9")
class Websphere9Jdk8Openj9 extends WebsphereSmokeTest {
}