/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest.appserver;

import io.opentelemetry.smoketest.SmokeTestOptions;
import io.opentelemetry.smoketest.TargetWaitStrategy;
import java.time.Duration;

abstract class JettySmokeTest extends AppServerTest {

  @Override
  protected void configure(SmokeTestOptions<AppServerImage> options) {
    configureOptions(options);
  }

  static SmokeTestOptions<AppServerImage> configureOptions(
      SmokeTestOptions<AppServerImage> options) {
    return options
        .image(
            appServerImage(
                "ghcr.io/open-telemetry/opentelemetry-java-instrumentation/smoke-test-servlet-jetty"))
        .waitStrategy(new TargetWaitStrategy.Log(Duration.ofMinutes(1), ".*Started Server.*"));
  }

  @AppServer(version = "9.4.58", jdk = "8")
  static class Jetty9Jdk8 extends JettySmokeTest {}

  @AppServer(version = "9.4.58", jdk = "8-openj9")
  static class Jetty9Jdk8Openj9 extends JettySmokeTest {}

  @AppServer(version = "9.4.58", jdk = "11")
  static class Jetty9Jdk11 extends JettySmokeTest {}

  @AppServer(version = "9.4.58", jdk = "11-openj9")
  static class Jetty9Jdk11Openj9 extends JettySmokeTest {}

  @AppServer(version = "9.4.58", jdk = "17")
  static class Jetty9Jdk17 extends JettySmokeTest {}

  @AppServer(version = "9.4.58", jdk = "17-openj9")
  static class Jetty9Jdk17Openj9 extends JettySmokeTest {}

  @AppServer(version = "9.4.58", jdk = "21")
  static class Jetty9Jdk21 extends JettySmokeTest {}

  @AppServer(version = "9.4.58", jdk = "21-openj9")
  static class Jetty9Jdk21Openj9 extends JettySmokeTest {}

  @AppServer(version = "9.4.58", jdk = "25")
  static class Jetty9Jdk25 extends JettySmokeTest {}

  @AppServer(version = "9.4.58", jdk = "25-openj9")
  static class Jetty9Jdk25Openj9 extends JettySmokeTest {}

  @AppServer(version = "10.0.26", jdk = "11")
  static class Jetty10Jdk11 extends JettySmokeTest {}

  @AppServer(version = "10.0.26", jdk = "11-openj9")
  static class Jetty10Jdk11Openj9 extends JettySmokeTest {}

  @AppServer(version = "10.0.26", jdk = "17")
  static class Jetty10Jdk17 extends JettySmokeTest {}

  @AppServer(version = "10.0.26", jdk = "17-openj9")
  static class Jetty10Jdk17Openj9 extends JettySmokeTest {}

  @AppServer(version = "10.0.26", jdk = "21")
  static class Jetty10Jdk21 extends JettySmokeTest {}

  @AppServer(version = "10.0.26", jdk = "21-openj9")
  static class Jetty10Jdk21Openj9 extends JettySmokeTest {}

  @AppServer(version = "10.0.26", jdk = "25")
  static class Jetty10Jdk25 extends JettySmokeTest {}

  @AppServer(version = "10.0.26", jdk = "25-openj9")
  static class Jetty10Jdk25Openj9 extends JettySmokeTest {}

  @AppServer(version = "11.0.26", jdk = "11")
  static class Jetty11Jdk11 extends JettySmokeTest {}

  @AppServer(version = "11.0.26", jdk = "11-openj9")
  static class Jetty11Jdk11Openj9 extends JettySmokeTest {}

  @AppServer(version = "11.0.26", jdk = "17")
  static class Jetty11Jdk17 extends JettySmokeTest {}

  @AppServer(version = "11.0.26", jdk = "17-openj9")
  static class Jetty11Jdk17Openj9 extends JettySmokeTest {}

  @AppServer(version = "11.0.26", jdk = "21")
  static class Jetty11Jdk21 extends JettySmokeTest {}

  @AppServer(version = "11.0.26", jdk = "21-openj9")
  static class Jetty11Jdk21Openj9 extends JettySmokeTest {}

  @AppServer(version = "11.0.26", jdk = "25")
  static class Jetty11Jdk25 extends JettySmokeTest {}

  @AppServer(version = "11.0.26", jdk = "25-openj9")
  static class Jetty11Jdk25Openj9 extends JettySmokeTest {}

  @AppServer(version = "12.0.28", jdk = "17")
  static class Jetty12Jdk17 extends JettySmokeTest {}

  @AppServer(version = "12.0.28", jdk = "17-openj9")
  static class Jetty12Jdk17Openj9 extends JettySmokeTest {}

  @AppServer(version = "12.0.28", jdk = "21")
  static class Jetty12Jdk21 extends JettySmokeTest {}

  @AppServer(version = "12.0.28", jdk = "21-openj9")
  static class Jetty12Jdk21Openj9 extends JettySmokeTest {}

  @AppServer(version = "12.0.28", jdk = "25")
  static class Jetty12Jdk25 extends JettySmokeTest {}

  @AppServer(version = "12.0.28", jdk = "25-openj9")
  static class Jetty12Jdk25Openj9 extends JettySmokeTest {}
}
