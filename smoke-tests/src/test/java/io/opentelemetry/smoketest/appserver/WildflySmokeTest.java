/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest.appserver;

import io.opentelemetry.smoketest.SmokeTestOptions;
import io.opentelemetry.smoketest.TargetWaitStrategy;
import java.time.Duration;

abstract class WildflySmokeTest extends AppServerTest {

  @Override
  protected void configure(SmokeTestOptions<AppServerImage> options) {
    options
        .image(
            appServerImage(
                "ghcr.io/open-telemetry/opentelemetry-java-instrumentation/smoke-test-servlet-wildfly"))
        .waitStrategy(new TargetWaitStrategy.Log(Duration.ofMinutes(1), ".*started in.*"));
  }

  @AppServer(version = "13.0.0.Final", jdk = "8")
  static class Wildfly13Jdk8 extends WildflySmokeTest {}

  @AppServer(version = "13.0.0.Final", jdk = "8-openj9")
  static class Wildfly13Jdk8Openj9 extends WildflySmokeTest {}

  @AppServer(version = "17.0.1.Final", jdk = "8")
  static class Wildfly17Jdk8 extends WildflySmokeTest {}

  @AppServer(version = "17.0.1.Final", jdk = "8-openj9")
  static class Wildfly17Jdk8Openj9 extends WildflySmokeTest {}

  @AppServer(version = "17.0.1.Final", jdk = "11")
  static class Wildfly17Jdk11 extends WildflySmokeTest {}

  @AppServer(version = "17.0.1.Final", jdk = "11-openj9")
  static class Wildfly17Jdk11Openj9 extends WildflySmokeTest {}

  @AppServer(version = "17.0.1.Final", jdk = "17")
  static class Wildfly17Jdk17 extends WildflySmokeTest {}

  @AppServer(version = "17.0.1.Final", jdk = "17-openj9")
  static class Wildfly17Jdk17Openj9 extends WildflySmokeTest {}

  @AppServer(version = "17.0.1.Final", jdk = "21")
  static class Wildfly17Jdk21 extends WildflySmokeTest {}

  @AppServer(version = "17.0.1.Final", jdk = "21-openj9")
  static class Wildfly17Jdk21Openj9 extends WildflySmokeTest {}

  @AppServer(version = "21.0.0.Final", jdk = "8")
  static class Wildfly21Jdk8 extends WildflySmokeTest {}

  @AppServer(version = "21.0.0.Final", jdk = "8-openj9")
  static class Wildfly21Jdk8Openj9 extends WildflySmokeTest {}

  @AppServer(version = "21.0.0.Final", jdk = "11")
  static class Wildfly21Jdk11 extends WildflySmokeTest {}

  @AppServer(version = "21.0.0.Final", jdk = "11-openj9")
  static class Wildfly21Jdk11Openj9 extends WildflySmokeTest {}

  @AppServer(version = "21.0.0.Final", jdk = "17")
  static class Wildfly21Jdk17 extends WildflySmokeTest {}

  @AppServer(version = "21.0.0.Final", jdk = "17-openj9")
  static class Wildfly21Jdk17Openj9 extends WildflySmokeTest {}

  @AppServer(version = "21.0.0.Final", jdk = "21")
  static class Wildfly21Jdk21 extends WildflySmokeTest {}

  @AppServer(version = "21.0.0.Final", jdk = "21-openj9")
  static class Wildfly21Jdk21Openj9 extends WildflySmokeTest {}

  @AppServer(version = "28.0.1.Final", jdk = "11")
  static class Wildfly28Jdk11 extends WildflySmokeTest {}

  @AppServer(version = "28.0.1.Final", jdk = "11-openj9")
  static class Wildfly28Jdk11Openj9 extends WildflySmokeTest {}

  @AppServer(version = "28.0.1.Final", jdk = "17")
  static class Wildfly28Jdk17 extends WildflySmokeTest {}

  @AppServer(version = "28.0.1.Final", jdk = "17-openj9")
  static class Wildfly28Jdk17Openj9 extends WildflySmokeTest {}

  @AppServer(version = "28.0.1.Final", jdk = "21")
  static class Wildfly28Jdk21 extends WildflySmokeTest {}

  @AppServer(version = "28.0.1.Final", jdk = "21-openj9")
  static class Wildfly28Jdk21Openj9 extends WildflySmokeTest {}

  @AppServer(version = "28.0.1.Final", jdk = "25")
  static class Wildfly28Jdk25 extends WildflySmokeTest {}

  @AppServer(version = "28.0.1.Final", jdk = "25-openj9")
  static class Wildfly28Jdk25Openj9 extends WildflySmokeTest {}

  @AppServer(version = "29.0.1.Final", jdk = "11")
  static class Wildfly29Jdk11 extends WildflySmokeTest {}

  @AppServer(version = "29.0.1.Final", jdk = "11-openj9")
  static class Wildfly29Jdk11Openj9 extends WildflySmokeTest {}

  @AppServer(version = "29.0.1.Final", jdk = "17")
  static class Wildfly29Jdk17 extends WildflySmokeTest {}

  @AppServer(version = "29.0.1.Final", jdk = "17-openj9")
  static class Wildfly29Jdk17Openj9 extends WildflySmokeTest {}

  @AppServer(version = "29.0.1.Final", jdk = "21")
  static class Wildfly29Jdk21 extends WildflySmokeTest {}

  @AppServer(version = "29.0.1.Final", jdk = "21-openj9")
  static class Wildfly29Jdk21Openj9 extends WildflySmokeTest {}

  @AppServer(version = "29.0.1.Final", jdk = "25")
  static class Wildfly29Jdk25 extends WildflySmokeTest {}

  @AppServer(version = "29.0.1.Final", jdk = "25-openj9")
  static class Wildfly29Jdk25Openj9 extends WildflySmokeTest {}

  @AppServer(version = "30.0.1.Final", jdk = "11")
  static class Wildfly30Jdk11 extends WildflySmokeTest {}

  @AppServer(version = "30.0.1.Final", jdk = "11-openj9")
  static class Wildfly30Jdk11Openj9 extends WildflySmokeTest {}

  @AppServer(version = "30.0.1.Final", jdk = "17")
  static class Wildfly30Jdk17 extends WildflySmokeTest {}

  @AppServer(version = "30.0.1.Final", jdk = "17-openj9")
  static class Wildfly30Jdk17Openj9 extends WildflySmokeTest {}

  @AppServer(version = "30.0.1.Final", jdk = "21")
  static class Wildfly30Jdk21 extends WildflySmokeTest {}

  @AppServer(version = "30.0.1.Final", jdk = "21-openj9")
  static class Wildfly30Jdk21Openj9 extends WildflySmokeTest {}

  @AppServer(version = "30.0.1.Final", jdk = "25")
  static class Wildfly30Jdk25 extends WildflySmokeTest {}

  @AppServer(version = "30.0.1.Final", jdk = "25-openj9")
  static class Wildfly30Jdk25Openj9 extends WildflySmokeTest {}
}
