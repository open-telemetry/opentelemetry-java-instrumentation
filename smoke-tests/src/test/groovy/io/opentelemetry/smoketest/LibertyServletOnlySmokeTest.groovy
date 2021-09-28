/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest

@AppServer(version = "20.0.0.12", jdk = "8")
@AppServer(version = "20.0.0.12", jdk = "8-openj9")
@AppServer(version = "20.0.0.12", jdk = "11")
@AppServer(version = "20.0.0.12", jdk = "11-openj9")
@AppServer(version = "20.0.0.12", jdk = "17")
@AppServer(version = "20.0.0.12", jdk = "16-openj9")
class LibertyServletOnlySmokeTest extends LibertySmokeTest {

  @Override
  protected List<ResourceMapping> getExtraResources() {
    [
      // server.xml path on linux containers
      ResourceMapping.of("liberty-servlet.xml", "/config/server.xml"),
      // server.xml path on windows containers
      ResourceMapping.of("liberty-servlet.xml", "/server/usr/servers/defaultServer/server.xml"),
    ]
  }

  @Override
  protected String getSpanName(String path) {
    switch (path) {
      case "/app/hello.txt":
      case "/app/file-that-does-not-exist":
        return "HTTP GET"
    }
    return super.getSpanName(path)
  }
}
