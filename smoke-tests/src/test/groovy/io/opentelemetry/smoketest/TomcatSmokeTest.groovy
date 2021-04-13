/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest

@AppServer(version = "7.0.107", jdk = "8")
@AppServer(version = "7.0.107", jdk = "8-openj9")
@AppServer(version = "8.5.60", jdk = "8")
@AppServer(version = "8.5.60", jdk = "8-openj9")
@AppServer(version = "8.5.60", jdk = "11")
@AppServer(version = "8.5.60", jdk = "11-openj9")
@AppServer(version = "9.0.40", jdk = "8")
@AppServer(version = "9.0.40", jdk = "8-openj9")
@AppServer(version = "9.0.40", jdk = "11")
@AppServer(version = "9.0.40", jdk = "11-openj9")
@AppServer(version = "10.0.4", jdk = "11")
@AppServer(version = "10.0.4", jdk = "11-openj9")
@AppServer(version = "10.0.4", jdk = "15")
@AppServer(version = "10.0.4", jdk = "15-openj9")
class TomcatSmokeTest extends AppServerTest {

  protected String getTargetImagePrefix() {
    "ghcr.io/open-telemetry/java-test-containers:tomcat"
  }
}
