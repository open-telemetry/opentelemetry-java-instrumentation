/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.spring.smoketest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.client.RestClient;

@SpringBootTest(
    classes = {
      OtelSpringStarterSmokeTestApplication.class,
      AbstractOtelSpringStarterSmokeTest.TestConfiguration.class,
      SpringSmokeOtelConfiguration.class
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      // The headers are simply set here to make sure that headers can be parsed
      "otel.exporter.otlp.headers.c=3",
      "otel.instrumentation.runtime-telemetry.emit-experimental-metrics=true",
      "otel.instrumentation.runtime-telemetry.emit-experimental-jfr-metrics=true",
      "otel.instrumentation.runtime-telemetry.experimental.jfr-metrics.included=*",
      "otel.instrumentation.common.thread_details.enabled=true",
      "logging.level.org.springframework.boot.autoconfigure=DEBUG",
    })
class OtelSpringStarterSmokeTest extends AbstractOtelSpringStarterSmokeTest {

  @Autowired private RestClient.Builder restClientBuilder;

  @Override
  void makeClientCall() {
    restClientBuilder
        .baseUrl("http://localhost:" + port)
        .build()
        .get()
        .uri(OtelSpringStarterSmokeTestController.PING)
        .header("key", "value")
        .retrieve()
        .body(String.class);
  }

  @Override
  void httpClientCall(String path) {
    RestClient restClient = restClientBuilder.baseUrl("http://localhost:" + port).build();
    restClient.get().uri(path).retrieve().body(String.class);
  }
}
