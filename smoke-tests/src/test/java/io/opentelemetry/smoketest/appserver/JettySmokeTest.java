/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest.appserver;

import io.opentelemetry.smoketest.SmokeTestInstrumentationExtension;
import io.opentelemetry.smoketest.TargetWaitStrategy;
import java.time.Duration;
import org.junit.jupiter.api.extension.RegisterExtension;

public abstract class JettySmokeTest extends AppServerTest {

  @RegisterExtension
  static final SmokeTestInstrumentationExtension<AppServerImage> testing = builder().build();

  @Override
  protected SmokeTestInstrumentationExtension<AppServerImage> testing() {
    return testing;
  }

  static SmokeTestInstrumentationExtension.Builder<AppServerImage> builder() {
    return builder(
            "ghcr.io/open-telemetry/opentelemetry-java-instrumentation/smoke-test-servlet-jetty")
        .waitStrategy(new TargetWaitStrategy.Log(Duration.ofMinutes(1), ".*Started Server.*"));
  }

  @AppServer(version = "9.4.53", jdk = "8")
  public static class Jetty9Jdk8 extends JettySmokeTest {}

  @AppServer(version = "9.4.53", jdk = "8-openj9")
  public static class Jetty9Jdk8Openj9 extends JettySmokeTest {}

  @AppServer(version = "9.4.53", jdk = "11")
  public static class Jetty9Jdk11 extends JettySmokeTest {}

  @AppServer(version = "9.4.53", jdk = "11-openj9")
  public static class Jetty9Jdk11Openj9 extends JettySmokeTest {}

  @AppServer(version = "9.4.53", jdk = "17")
  public static class Jetty9Jdk17 extends JettySmokeTest {}

  @AppServer(version = "9.4.53", jdk = "17-openj9")
  public static class Jetty9Jdk17Openj9 extends JettySmokeTest {}

  @AppServer(version = "9.4.53", jdk = "21")
  public static class Jetty9Jdk21 extends JettySmokeTest {}

  @AppServer(version = "9.4.53", jdk = "21-openj9")
  public static class Jetty9Jdk21Openj9 extends JettySmokeTest {}

  @AppServer(version = "9.4.53", jdk = "23")
  public static class Jetty9Jdk23 extends JettySmokeTest {}

  @AppServer(version = "9.4.53", jdk = "23-openj9")
  public static class Jetty9Jdk23Openj9 extends JettySmokeTest {}

  @AppServer(version = "10.0.19", jdk = "11")
  public static class Jetty10Jdk11 extends JettySmokeTest {}

  @AppServer(version = "10.0.19", jdk = "11-openj9")
  public static class Jetty10Jdk11Openj9 extends JettySmokeTest {}

  @AppServer(version = "10.0.19", jdk = "17")
  public static class Jetty10Jdk17 extends JettySmokeTest {}

  @AppServer(version = "10.0.19", jdk = "17-openj9")
  public static class Jetty10Jdk17Openj9 extends JettySmokeTest {}

  @AppServer(version = "10.0.19", jdk = "21")
  public static class Jetty10Jdk21 extends JettySmokeTest {}

  @AppServer(version = "10.0.19", jdk = "21-openj9")
  public static class Jetty10Jdk21Openj9 extends JettySmokeTest {}

  @AppServer(version = "10.0.19", jdk = "23")
  public static class Jetty10Jdk23 extends JettySmokeTest {}

  @AppServer(version = "10.0.19", jdk = "23-openj9")
  public static class Jetty10Jdk23Openj9 extends JettySmokeTest {}

  @AppServer(version = "11.0.19", jdk = "11")
  public static class Jetty11Jdk11 extends JettySmokeTest {}

  @AppServer(version = "11.0.19", jdk = "11-openj9")
  public static class Jetty11Jdk11Openj9 extends JettySmokeTest {}

  @AppServer(version = "11.0.19", jdk = "17")
  public static class Jetty11Jdk17 extends JettySmokeTest {}

  @AppServer(version = "11.0.19", jdk = "17-openj9")
  public static class Jetty11Jdk17Openj9 extends JettySmokeTest {}

  @AppServer(version = "11.0.19", jdk = "21")
  public static class Jetty11Jdk21 extends JettySmokeTest {}

  @AppServer(version = "11.0.19", jdk = "21-openj9")
  public static class Jetty11Jdk21Openj9 extends JettySmokeTest {}

  @AppServer(version = "11.0.19", jdk = "23")
  public static class Jetty11Jdk23 extends JettySmokeTest {}

  @AppServer(version = "11.0.19", jdk = "23-openj9")
  public static class Jetty11Jdk23Openj9 extends JettySmokeTest {}

  @AppServer(version = "12.0.6", jdk = "17")
  public static class Jetty12Jdk17 extends JettySmokeTest {}

  @AppServer(version = "12.0.6", jdk = "17-openj9")
  public static class Jetty12Jdk17Openj9 extends JettySmokeTest {}

  @AppServer(version = "12.0.6", jdk = "21")
  public static class Jetty12Jdk21 extends JettySmokeTest {}

  @AppServer(version = "12.0.6", jdk = "21-openj9")
  public static class Jetty12Jdk21Openj9 extends JettySmokeTest {}

  @AppServer(version = "12.0.6", jdk = "23")
  public static class Jetty12Jdk23 extends JettySmokeTest {}

  @AppServer(version = "12.0.6", jdk = "23-openj9")
  public static class Jetty12Jdk23Openj9 extends JettySmokeTest {}
}
