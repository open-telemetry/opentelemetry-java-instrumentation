/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest.appserver;

import io.opentelemetry.smoketest.SmokeTestOptions;

abstract class JettyJpmsSmokeTest extends AppServerTest {

  @Override
  protected void configure(SmokeTestOptions<AppServerImage> options) {
    JettySmokeTest.configureOptions(options).command("java", "-jar", "/server/start.jar", "--jpms");
  }

  @AppServer(version = "11.0.26", jdk = "11", inReducedMatrix = true)
  static class Jetty11JpmsJdk11 extends JettyJpmsSmokeTest {}

  @AppServer(version = "11.0.26", jdk = "17", inReducedMatrix = true)
  static class Jetty11JpmsJdk17 extends JettyJpmsSmokeTest {}

  @AppServer(version = "11.0.26", jdk = "21", inReducedMatrix = true)
  static class Jetty11JpmsJdk21 extends JettyJpmsSmokeTest {}

  @AppServer(version = "11.0.26", jdk = "25", inReducedMatrix = true)
  static class Jetty11JpmsJdk25 extends JettyJpmsSmokeTest {}
}
