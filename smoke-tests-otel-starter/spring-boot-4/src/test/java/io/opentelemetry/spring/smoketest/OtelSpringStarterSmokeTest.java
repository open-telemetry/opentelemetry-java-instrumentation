/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.spring.smoketest;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.semconv.HttpAttributes;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.RequestEntity;
import org.springframework.web.client.RestClient;
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
      "otel.instrumentation.runtime-telemetry-java17.enable-all=true",
      "otel.instrumentation.common.thread_details.enabled=true",
      "logging.level.org.springframework.boot.autoconfigure=DEBUG",
    })
@AutoConfigureTestRestTemplate
class OtelSpringStarterSmokeTest extends AbstractOtelSpringStarterSmokeTest {

  @Autowired protected TestRestTemplate testRestTemplate;
  @Autowired private RestTemplateBuilder restTemplateBuilder;
  @Autowired private RestClient.Builder restClientBuilder;

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
  void restTemplateCall(String path) {
    RestTemplate restTemplate = restTemplateBuilder.rootUri("http://localhost:" + port).build();
    restTemplate.getForObject(path, String.class);
  }

  @Test
  void restClientBuilder() {
    RestClient restClient = restClientBuilder.baseUrl("http://localhost:" + port).build();
    restClient.get().uri(OtelSpringStarterSmokeTestController.PING).retrieve().body(String.class);

    testing.waitAndAssertTraces(
        traceAssert ->
            traceAssert.hasSpansSatisfyingExactly(
                span -> HttpSpanDataAssert.create(span).assertClientGetRequest("/ping"),
                span ->
                    span.hasKind(SpanKind.SERVER).hasAttribute(HttpAttributes.HTTP_ROUTE, "/ping"),
                AbstractSpringStarterSmokeTest::withSpanAssert));
  }
}
