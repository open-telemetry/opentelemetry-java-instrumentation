/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest

abstract class LibertyServletOnlySmokeTest extends LibertySmokeTest {

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

@AppServer(version = "20.0.0.12", jdk = "11")
class LibertyServletOnly20Jdk11 extends LibertySmokeTest {
}
@AppServer(version = "21.0.0.10", jdk = "11")
class LibertyServletOnly21Jdk11 extends LibertySmokeTest {
}
