/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest.appserver;

import io.opentelemetry.smoketest.SmokeTestInstrumentationExtension;
import io.opentelemetry.smoketest.TargetWaitStrategy;
import java.time.Duration;
import org.junit.jupiter.api.extension.RegisterExtension;

public abstract class TomcatSmokeTest extends AppServerTest {

  @RegisterExtension
  static final SmokeTestInstrumentationExtension<AppServerImage> testing =
      builder("ghcr.io/open-telemetry/opentelemetry-java-instrumentation/smoke-test-servlet-tomcat")
          .waitStrategy(new TargetWaitStrategy.Log(Duration.ofMinutes(1), ".*Server startup in.*"))
          .build();

  @Override
  protected SmokeTestInstrumentationExtension<AppServerImage> testing() {
    return testing;
  }

  @Override
  protected boolean expectServerSpan() {
    return !"7.0.109".equals(serverVersion);
  }

  // --- AppServer test classes ---

  @AppServer(version = "7.0.109", jdk = "8")
  public static class Tomcat7Jdk8 extends TomcatSmokeTest {}

  @AppServer(version = "7.0.109", jdk = "8-openj9")
  public static class Tomcat7Jdk8Openj9 extends TomcatSmokeTest {}

  @AppServer(version = "8.5.98", jdk = "8")
  public static class Tomcat8Jdk8 extends TomcatSmokeTest {}

  @AppServer(version = "8.5.98", jdk = "8-openj9")
  public static class Tomcat8Jdk8Openj9 extends TomcatSmokeTest {}

  @AppServer(version = "8.5.98", jdk = "11")
  public static class Tomcat8Jdk11 extends TomcatSmokeTest {}

  @AppServer(version = "8.5.98", jdk = "11-openj9")
  public static class Tomcat8Jdk11Openj9 extends TomcatSmokeTest {}

  @AppServer(version = "8.5.98", jdk = "17")
  public static class Tomcat8Jdk17 extends TomcatSmokeTest {}

  @AppServer(version = "8.5.98", jdk = "17-openj9")
  public static class Tomcat8Jdk17Openj9 extends TomcatSmokeTest {}

  @AppServer(version = "8.5.98", jdk = "21")
  public static class Tomcat8Jdk21 extends TomcatSmokeTest {}

  @AppServer(version = "8.5.98", jdk = "21-openj9")
  public static class Tomcat8Jdk21Openj9 extends TomcatSmokeTest {}

  @AppServer(version = "8.5.98", jdk = "23")
  public static class Tomcat8Jdk23 extends TomcatSmokeTest {}

  @AppServer(version = "8.5.98", jdk = "23-openj9")
  public static class Tomcat8Jdk23Openj9 extends TomcatSmokeTest {}

  @AppServer(version = "9.0.85", jdk = "8")
  public static class Tomcat9Jdk8 extends TomcatSmokeTest {}

  @AppServer(version = "9.0.85", jdk = "8-openj9")
  public static class Tomcat9Jdk8Openj9 extends TomcatSmokeTest {}

  @AppServer(version = "9.0.85", jdk = "11")
  public static class Tomcat9Jdk11 extends TomcatSmokeTest {}

  @AppServer(version = "9.0.85", jdk = "11-openj9")
  public static class Tomcat9Jdk11Openj9 extends TomcatSmokeTest {}

  @AppServer(version = "9.0.85", jdk = "17")
  public static class Tomcat9Jdk17 extends TomcatSmokeTest {}

  @AppServer(version = "9.0.85", jdk = "17-openj9")
  public static class Tomcat9Jdk17Openj9 extends TomcatSmokeTest {}

  @AppServer(version = "9.0.85", jdk = "21")
  public static class Tomcat9Jdk21 extends TomcatSmokeTest {}

  @AppServer(version = "9.0.85", jdk = "21-openj9")
  public static class Tomcat9Jdk21Openj9 extends TomcatSmokeTest {}

  @AppServer(version = "9.0.85", jdk = "23")
  public static class Tomcat9Jdk23 extends TomcatSmokeTest {}

  @AppServer(version = "9.0.85", jdk = "23-openj9")
  public static class Tomcat9Jdk23Openj9 extends TomcatSmokeTest {}

  @AppServer(version = "10.1.18", jdk = "11")
  public static class Tomcat10Jdk11 extends TomcatSmokeTest {}

  @AppServer(version = "10.1.18", jdk = "11-openj9")
  public static class Tomcat10Jdk11Openj9 extends TomcatSmokeTest {}

  @AppServer(version = "10.1.18", jdk = "17")
  public static class Tomcat10Jdk17 extends TomcatSmokeTest {}

  @AppServer(version = "10.1.18", jdk = "17-openj9")
  public static class Tomcat10Jdk17Openj9 extends TomcatSmokeTest {}

  @AppServer(version = "10.1.18", jdk = "21")
  public static class Tomcat10Jdk21 extends TomcatSmokeTest {}

  @AppServer(version = "10.1.18", jdk = "21-openj9")
  public static class Tomcat10Jdk21Openj9 extends TomcatSmokeTest {}

  @AppServer(version = "10.1.18", jdk = "23")
  public static class Tomcat10Jdk23 extends TomcatSmokeTest {}

  @AppServer(version = "10.1.18", jdk = "23-openj9")
  public static class Tomcat10Jdk23Openj9 extends TomcatSmokeTest {}
}
