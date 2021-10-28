/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest

// jetty test with java module system
@AppServer(version = "11.0.7", jdk = "11")
class JettyJpmsSmokeTest extends AppServerTest {

  @Override
  protected String getTargetImagePrefix() {
    "ghcr.io/open-telemetry/opentelemetry-java-instrumentation/smoke-test-servlet-jetty"
  }

  @Override
  protected String[] getCommand() {
    // --jpms flags enables using java module system
    return ["java", "-jar", "/server/start.jar", "--jpms"]
  }
}
