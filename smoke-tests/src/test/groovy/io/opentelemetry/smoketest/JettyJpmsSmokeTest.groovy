/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest

// jetty test with java module system
abstract class JettyJpmsSmokeTest extends JettySmokeTest {

  @Override
  protected String[] getCommand() {
    // --jpms flags enables using java module system
    return ["java", "-jar", "/server/start.jar", "--jpms"]
  }
}

@AppServer(version = "11.0.7", jdk = "11")
class Jetty11JpmsJdk11 extends JettyJpmsSmokeTest {
}

@AppServer(version = "11.0.7", jdk = "17")
class Jetty11JpmsJdk17 extends JettyJpmsSmokeTest {
}

@AppServer(version = "11.0.7", jdk = "18")
class Jetty11JpmsJdk18 extends JettyJpmsSmokeTest {
}
