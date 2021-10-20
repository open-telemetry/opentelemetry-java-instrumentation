/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest

@AppServer(version = "9.4.39", jdk = "8")
@AppServer(version = "9.4.39", jdk = "8-openj9")
@AppServer(version = "9.4.39", jdk = "11")
@AppServer(version = "9.4.39", jdk = "11-openj9")
@AppServer(version = "9.4.39", jdk = "17")
@AppServer(version = "9.4.39", jdk = "16-openj9")
@AppServer(version = "10.0.7", jdk = "11")
@AppServer(version = "10.0.7", jdk = "11-openj9")
@AppServer(version = "10.0.7", jdk = "17")
@AppServer(version = "10.0.7", jdk = "16-openj9")
@AppServer(version = "11.0.7", jdk = "11")
@AppServer(version = "11.0.7", jdk = "11-openj9")
@AppServer(version = "11.0.7", jdk = "17")
@AppServer(version = "11.0.7", jdk = "16-openj9")
class JettySmokeTest extends AppServerTest {

  protected String getTargetImagePrefix() {
    "ghcr.io/open-telemetry/opentelemetry-java-instrumentation/smoke-test-servlet-jetty"
  }
}
