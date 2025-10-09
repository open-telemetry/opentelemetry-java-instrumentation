/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest.appserver;

import io.opentelemetry.smoketest.SmokeTestOptions;
import io.opentelemetry.smoketest.TargetWaitStrategy;
import java.time.Duration;

abstract class LibertySmokeTest extends AppServerTest {

  @Override
  protected void configure(SmokeTestOptions<AppServerImage> options) {
    configureOptions(options);
  }

  static SmokeTestOptions<AppServerImage> configureOptions(
      SmokeTestOptions<AppServerImage> options) {
    return options
        .image(
            appServerImage(
                "ghcr.io/open-telemetry/opentelemetry-java-instrumentation/smoke-test-servlet-liberty"))
        .waitStrategy(
            new TargetWaitStrategy.Log(
                Duration.ofMinutes(3), ".*server is ready to run a smarter planet.*"));
  }

  @AppServer(version = "20.0.0.12", jdk = "8")
  static class Liberty20Jdk8 extends LibertySmokeTest {}

  @AppServer(version = "20.0.0.12", jdk = "8-openj9")
  static class Liberty20Jdk8Openj9 extends LibertySmokeTest {}

  @AppServer(version = "20.0.0.12", jdk = "11")
  static class Liberty20Jdk11 extends LibertySmokeTest {}

  @AppServer(version = "20.0.0.12", jdk = "11-openj9")
  static class Liberty20Jdk11Openj9 extends LibertySmokeTest {}

  @AppServer(version = "21.0.0.12", jdk = "8")
  static class Liberty21Jdk8 extends LibertySmokeTest {}

  @AppServer(version = "21.0.0.12", jdk = "8-openj9")
  static class Liberty21Jdk8Openj9 extends LibertySmokeTest {}

  @AppServer(version = "21.0.0.12", jdk = "11")
  static class Liberty21Jdk11 extends LibertySmokeTest {}

  @AppServer(version = "21.0.0.12", jdk = "11-openj9")
  static class Liberty21Jdk11Openj9 extends LibertySmokeTest {}

  @AppServer(version = "21.0.0.12", jdk = "17")
  static class Liberty21Jdk17 extends LibertySmokeTest {}

  @AppServer(version = "21.0.0.12", jdk = "17-openj9")
  static class Liberty21Jdk17Openj9 extends LibertySmokeTest {}

  @AppServer(version = "22.0.0.12", jdk = "8")
  static class Liberty22Jdk8 extends LibertySmokeTest {}

  @AppServer(version = "22.0.0.12", jdk = "8-openj9")
  static class Liberty22Jdk8Openj9 extends LibertySmokeTest {}

  @AppServer(version = "22.0.0.12", jdk = "11")
  static class Liberty22Jdk11 extends LibertySmokeTest {}

  @AppServer(version = "22.0.0.12", jdk = "11-openj9")
  static class Liberty22Jdk11Openj9 extends LibertySmokeTest {}

  @AppServer(version = "22.0.0.12", jdk = "17")
  static class Liberty22Jdk17 extends LibertySmokeTest {}

  @AppServer(version = "22.0.0.12", jdk = "17-openj9")
  static class Liberty22Jdk17Openj9 extends LibertySmokeTest {}

  @AppServer(version = "23.0.0.12", jdk = "8")
  static class Liberty23Jdk8 extends LibertySmokeTest {
    @Override
    protected boolean testJsp() {
      return false;
    }
  }

  @AppServer(version = "23.0.0.12", jdk = "8-openj9")
  static class Liberty23Jdk8Openj9 extends LibertySmokeTest {
    @Override
    protected boolean testJsp() {
      return false;
    }
  }

  @AppServer(version = "23.0.0.12", jdk = "11")
  static class Liberty23Jdk11 extends LibertySmokeTest {}

  @AppServer(version = "23.0.0.12", jdk = "11-openj9")
  static class Liberty23Jdk11Openj9 extends LibertySmokeTest {}

  @AppServer(version = "23.0.0.12", jdk = "17")
  static class Liberty23Jdk17 extends LibertySmokeTest {}

  @AppServer(version = "23.0.0.12", jdk = "17-openj9")
  static class Liberty23Jdk17Openj9 extends LibertySmokeTest {}

  @AppServer(version = "23.0.0.12", jdk = "20")
  static class Liberty23Jdk20 extends LibertySmokeTest {}

  @AppServer(version = "23.0.0.12", jdk = "20-openj9")
  static class Liberty23Jdk20Openj9 extends LibertySmokeTest {}
}
