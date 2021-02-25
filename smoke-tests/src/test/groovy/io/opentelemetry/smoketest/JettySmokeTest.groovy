/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest

@AppServer(version = "9.4.35", jdk = "8")
@AppServer(version = "9.4.35", jdk = "8-openj9")
@AppServer(version = "9.4.35", jdk = "11")
@AppServer(version = "9.4.35", jdk = "11-openj9")
@AppServer(version = "10.0.0", jdk = "11")
@AppServer(version = "10.0.0", jdk = "11-openj9")
@AppServer(version = "10.0.0", jdk = "15")
@AppServer(version = "10.0.0", jdk = "15-openj9")
class JettySmokeTest extends AppServerTest {

  protected String getTargetImage(String jdk, String serverVersion) {
    "ghcr.io/open-telemetry/java-test-containers:jetty-${serverVersion}-jdk$jdk-20210223.592806654"
  }
}
