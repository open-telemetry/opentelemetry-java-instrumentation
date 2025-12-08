/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.spring.smoketest;

import java.net.URI;
import org.assertj.core.api.AbstractIterableAssert;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.RequestEntity;
import org.springframework.web.client.RestTemplate;

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
      "otel.instrumentation.runtime-telemetry.emit-experimental-telemetry=true",
      "otel.instrumentation.common.thread_details.enabled=true",
    })
class OtelSpringStarterSmokeTest extends AbstractOtelSpringStarterSmokeTest {

  @Autowired protected TestRestTemplate testRestTemplate;
  @Autowired private RestTemplateBuilder restTemplateBuilder;

  @Override
  void makeClientCall() {
    HttpHeaders headers = new HttpHeaders();
    headers.add("key", "value");

    testRestTemplate.exchange(
        new RequestEntity<>(
            null, headers, HttpMethod.GET, URI.create(OtelSpringStarterSmokeTestController.PING)),
        String.class);
  }

  @Override
  void restClientCall(String path) {
    RestTemplate restTemplate = restTemplateBuilder.rootUri("http://localhost:" + port).build();
    restTemplate.getForObject(path, String.class);
  }

  @Override
  protected void assertAdditionalMetrics() {
    testing.waitAndAssertMetrics(
        "io.opentelemetry.micrometer-1.5", "disk.total", AbstractIterableAssert::isNotEmpty);
  }
}
