/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest

import java.time.Duration

@AppServer(version = "7.0.9", jdk = "8")
@AppServer(version = "7.0.9", jdk = "8-openj9")
@AppServer(version = "7.1.4", jdk = "8")
@AppServer(version = "7.1.4", jdk = "8-openj9")
@AppServer(version = "8.0.8", jdk = "8")
@AppServer(version = "8.0.8", jdk = "8-openj9")
@AppServer(version = "8.0.8", jdk = "11")
@AppServer(version = "8.0.8", jdk = "11-openj9")
@AppServer(version = "8.0.8", jdk = "17")
@AppServer(version = "8.0.8", jdk = "16-openj9")
@AppServer(version = "9.0.0-M7", jdk = "8")
@AppServer(version = "9.0.0-M7", jdk = "8-openj9")
@AppServer(version = "9.0.0-M7", jdk = "11")
@AppServer(version = "9.0.0-M7", jdk = "11-openj9")
@AppServer(version = "9.0.0-M7", jdk = "17")
@AppServer(version = "9.0.0-M7", jdk = "16-openj9")
class TomeeSmokeTest extends AppServerTest {

  protected String getTargetImagePrefix() {
    "ghcr.io/open-telemetry/opentelemetry-java-instrumentation/smoke-test-servlet-tomee"
  }

  @Override
  protected TargetWaitStrategy getWaitStrategy() {
    return new TargetWaitStrategy.Log(Duration.ofMinutes(3), ".*Server startup in.*")
  }

  @Override
  protected String getSpanName(String path) {
    switch (path) {
      case "/this-is-definitely-not-there-but-there-should-be-a-trace-nevertheless":
        return "/*"
    }
    return super.getSpanName(path)
  }
}
