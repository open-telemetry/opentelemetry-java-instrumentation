/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest.appserver;

import io.opentelemetry.smoketest.AppServer;
import io.opentelemetry.smoketest.SmokeTestInstrumentationExtension;
import org.junit.jupiter.api.extension.RegisterExtension;

public abstract class JettyJpmsSmokeTest extends AppServerTest {

  @RegisterExtension
  static final SmokeTestInstrumentationExtension testing =
      JettySmokeTest.builder()
          // --jpms flag enables using java module system
          .command("java", "-jar", "/server/start.jar", "--jpms")
          .build();

  @Override
  protected SmokeTestInstrumentationExtension testing() {
    return testing;
  }

  @AppServer(version = "11.0.19", jdk = "11")
  public static class Jetty11JpmsJdk11 extends JettyJpmsSmokeTest {}

  @AppServer(version = "11.0.19", jdk = "17")
  public static class Jetty11JpmsJdk17 extends JettyJpmsSmokeTest {}

  @AppServer(version = "11.0.19", jdk = "21")
  public static class Jetty11JpmsJdk21 extends JettyJpmsSmokeTest {}

  @AppServer(version = "11.0.19", jdk = "23")
  public static class Jetty11JpmsJdk23 extends JettyJpmsSmokeTest {}
}
