/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest

@AppServer(version = "7.0.109", jdk = "8")
@AppServer(version = "7.0.109", jdk = "8-openj9")
@AppServer(version = "8.5.71", jdk = "8")
@AppServer(version = "8.5.71", jdk = "11")
@AppServer(version = "8.5.71", jdk = "17")
@AppServer(version = "8.5.70", jdk = "8-openj9")
@AppServer(version = "8.5.70", jdk = "11-openj9")
@AppServer(version = "9.0.53", jdk = "8")
@AppServer(version = "9.0.53", jdk = "11")
@AppServer(version = "9.0.53", jdk = "17")
@AppServer(version = "9.0.52", jdk = "8-openj9")
@AppServer(version = "9.0.52", jdk = "11-openj9")
// TODO (trask) there are Tomcat 10 images for Java 8 (both hotspot and openj9)
//  but smoke tests are currently failing on them
@AppServer(version = "10.0.11", jdk = "11")
@AppServer(version = "10.0.11", jdk = "17")
@AppServer(version = "10.0.8", jdk = "11-openj9")
class TomcatSmokeTest extends AppServerTest {

  protected String getTargetImagePrefix() {
    "ghcr.io/open-telemetry/opentelemetry-java-instrumentation/smoke-test-servlet-tomcat"
  }
}
