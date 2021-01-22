/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest

@AppServer(version = "9.4.35", jdk = "8")
@AppServer(version = "9.4.35", jdk = "11")
@AppServer(version = "10.0.0", jdk = "11")
@AppServer(version = "10.0.0", jdk = "15")
class JettySmokeTest extends AppServerTest {

  protected String getTargetImage(String jdk, String serverVersion) {
    "ghcr.io/open-telemetry/java-test-containers:jetty-${serverVersion}-jdk$jdk-20201215.422527843"
  }

  def getJettySpanName() {
    return serverVersion.startsWith("10.") ? "HandlerList.handle" : "HandlerCollection.handle"
  }

  @Override
  protected String getSpanName(String path) {
    switch (path) {
      case "/app/WEB-INF/web.xml":
      case "/this-is-definitely-not-there-but-there-should-be-a-trace-nevertheless":
        return getJettySpanName()
    }
    return path
  }
}
