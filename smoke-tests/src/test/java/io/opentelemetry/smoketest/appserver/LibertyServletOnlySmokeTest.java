/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest.appserver;

import io.opentelemetry.smoketest.ResourceMapping;
import io.opentelemetry.smoketest.SmokeTestInstrumentationExtension;
import org.junit.jupiter.api.extension.RegisterExtension;

public abstract class LibertyServletOnlySmokeTest extends AppServerTest {

  @RegisterExtension
  static final SmokeTestInstrumentationExtension<AppServerImage> testing =
      LibertySmokeTest.builder()
          .extraResources(
              ResourceMapping.of("liberty-servlet.xml", "/config/server.xml"),
              ResourceMapping.of(
                  "liberty-servlet.xml", "/server/usr/servers/defaultServer/server.xml"))
          .build();

  @Override
  protected SmokeTestInstrumentationExtension<AppServerImage> testing() {
    return testing;
  }

  @Override
  protected boolean testJsp() {
    return false;
  }

  @Override
  protected String getSpanName(String path) {
    if ("/app/hello.txt".equals(path) || "/app/file-that-does-not-exist".equals(path)) {
      return "GET";
    }
    return super.getSpanName(path);
  }

  @AppServer(version = "21.0.0.12", jdk = "11")
  public static class LibertyServletOnly21Jdk11 extends LibertyServletOnlySmokeTest {}

  @AppServer(version = "22.0.0.12", jdk = "11")
  public static class LibertyServletOnly22Jdk11 extends LibertyServletOnlySmokeTest {}

  @AppServer(version = "23.0.0.12", jdk = "11")
  public static class LibertyServletOnly23Jdk11 extends LibertyServletOnlySmokeTest {}
}
