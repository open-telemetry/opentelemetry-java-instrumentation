/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest.appserver;

import io.opentelemetry.smoketest.SmokeTestOptions;
import io.opentelemetry.smoketest.TargetWaitStrategy;
import java.time.Duration;

abstract class TomeeSmokeTest extends AppServerTest {

  @Override
  protected void configure(SmokeTestOptions<AppServerImage> options) {
    options
        .image(
            appServerImage(
                "ghcr.io/open-telemetry/opentelemetry-java-instrumentation/smoke-test-servlet-tomee"))
        .waitStrategy(new TargetWaitStrategy.Log(Duration.ofMinutes(3), ".*Server startup in.*"));
  }

  @Override
  protected String getSpanName(String path) {
    if ("/this-is-definitely-not-there-but-there-should-be-a-trace-nevertheless".equals(path)) {
      return "GET /*";
    }
    return super.getSpanName(path);
  }

  @AppServer(version = "7.0.9", jdk = "8")
  static class Tomee70Jdk8 extends TomeeSmokeTest {}

  @AppServer(version = "7.0.9", jdk = "8-openj9")
  static class Tomee70Jdk8Openj9 extends TomeeSmokeTest {}

  @AppServer(version = "7.1.4", jdk = "8")
  static class Tomee71Jdk8 extends TomeeSmokeTest {}

  @AppServer(version = "7.1.4", jdk = "8-openj9")
  static class Tomee71Jdk8Openj9 extends TomeeSmokeTest {}

  @AppServer(version = "8.0.16", jdk = "8")
  static class Tomee8Jdk8 extends TomeeSmokeTest {}

  @AppServer(version = "8.0.16", jdk = "8-openj9")
  static class Tomee8Jdk8Openj9 extends TomeeSmokeTest {}

  @AppServer(version = "8.0.16", jdk = "11")
  static class Tomee8Jdk11 extends TomeeSmokeTest {}

  @AppServer(version = "8.0.16", jdk = "11-openj9")
  static class Tomee8Jdk11Openj9 extends TomeeSmokeTest {}

  @AppServer(version = "8.0.16", jdk = "17")
  static class Tomee8Jdk17 extends TomeeSmokeTest {}

  @AppServer(version = "8.0.16", jdk = "17-openj9")
  static class Tomee8Jdk17Openj9 extends TomeeSmokeTest {}

  @AppServer(version = "8.0.16", jdk = "21")
  static class Tomee8Jdk21 extends TomeeSmokeTest {}

  @AppServer(version = "8.0.16", jdk = "21-openj9")
  static class Tomee8Jdk21Openj9 extends TomeeSmokeTest {}

  @AppServer(version = "8.0.16", jdk = "25")
  static class Tomee8Jdk25 extends TomeeSmokeTest {}

  @AppServer(version = "8.0.16", jdk = "25-openj9")
  static class Tomee8Jdk25Openj9 extends TomeeSmokeTest {}

  @AppServer(version = "9.1.2", jdk = "11")
  static class Tomee9Jdk11 extends TomeeSmokeTest {}

  @AppServer(version = "9.1.2", jdk = "11-openj9")
  static class Tomee9Jdk11Openj9 extends TomeeSmokeTest {}

  @AppServer(version = "9.1.2", jdk = "17")
  static class Tomee9Jdk17 extends TomeeSmokeTest {}

  @AppServer(version = "9.1.2", jdk = "17-openj9")
  static class Tomee9Jdk17Openj9 extends TomeeSmokeTest {}

  @AppServer(version = "9.1.2", jdk = "21")
  static class Tomee9Jdk21 extends TomeeSmokeTest {}

  @AppServer(version = "9.1.2", jdk = "21-openj9")
  static class Tomee9Jdk21Openj9 extends TomeeSmokeTest {}

  @AppServer(version = "9.1.2", jdk = "25")
  static class Tomee9Jdk25 extends TomeeSmokeTest {}

  @AppServer(version = "9.1.2", jdk = "25-openj9")
  static class Tomee9Jdk25Openj9 extends TomeeSmokeTest {}
}
