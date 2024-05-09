/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.spring.smoketest;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.metrics.LongHistogram;
import io.opentelemetry.api.metrics.Meter;
import java.util.Optional;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

@RestController
public class OtelSpringStarterSmokeTestController {

  public static final String PING = "/ping";
  public static final String REST_CLIENT = "/rest-client";
  public static final String REST_TEMPLATE = "/rest-template";
  public static final String TEST_HISTOGRAM = "histogram-test-otel-spring-starter";
  public static final String METER_SCOPE_NAME =
      OtelSpringStarterSmokeTestApplication.class.getName();
  private final LongHistogram histogram;
  private final Optional<RestTemplate> restTemplate;
  private final Optional<RestClient> restClient;

  public OtelSpringStarterSmokeTestController(
      OpenTelemetry openTelemetry,
      RestClient.Builder restClientBuilder,
      RestTemplateBuilder restTemplateBuilder,
      Optional<ServletWebServerApplicationContext> server) {
    Meter meter = openTelemetry.getMeter(METER_SCOPE_NAME);
    histogram = meter.histogramBuilder(TEST_HISTOGRAM).ofLongs().build();
    Optional<String> rootUri = server.map(s -> "http://localhost:" + s.getWebServer().getPort());
    restClient = rootUri.map(uri -> restClientBuilder.baseUrl(uri).build());
    restTemplate = rootUri.map(uri -> restTemplateBuilder.rootUri(uri).build());
  }

  @GetMapping(PING)
  public String ping() {
    histogram.record(10);
    return "pong";
  }

  @GetMapping(REST_CLIENT)
  public String restClient() {
    return restClient
        .map(c -> c.get().uri(PING).retrieve().body(String.class))
        .orElseThrow(() -> new IllegalStateException("RestClient not available"));
  }

  @GetMapping(REST_TEMPLATE)
  public String restTemplate() {
    return restTemplate
        .map(t -> t.getForObject(PING, String.class))
        .orElseThrow(() -> new IllegalStateException("RestTemplate not available"));
  }
}
