/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.spring.smoketest;

import static org.apache.commons.lang3.reflect.MethodUtils.invokeMethod;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootVersion;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;

@SpringBootTest(
    classes = {
      OtelSpringStarterSmokeTestApplication.class,
      AbstractOtelSpringStarterSmokeTest.TestConfiguration.class,
      SpringSmokeOtelConfiguration.class
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      // The headers are simply set here to make sure that headers can be parsed
      "otel.exporter.otlp.headers.c=3"
    })
class OtelSpringStarterSmokeTest extends AbstractOtelSpringStarterSmokeTest {

  @Autowired ApplicationContext context;
  @LocalServerPort private int port;

  @Test
  void restClient() throws Exception {
    int version = Integer.parseInt(SpringBootVersion.getVersion().substring(2, 3));
    assumeTrue(version >= 2); // rest client is available since Spring Boot 3.2

    testing.clearAllExportedData();

    Object builder =
        context.getBean(Class.forName("org.springframework.web.client.RestClient$Builder"));
    builder = invokeMethod(builder, "baseUrl", "http://localhost:" + port);
    Object restClient = invokeMethod(builder, "build");
    Object spec = invokeMethod(restClient, "get");
    invokeMethod(spec, "uri", OtelSpringStarterSmokeTestController.PING);

    assertClient();
  }
}
