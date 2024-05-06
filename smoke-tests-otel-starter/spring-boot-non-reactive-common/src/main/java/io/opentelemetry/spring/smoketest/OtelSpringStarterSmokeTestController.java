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
import org.springframework.web.client.RestTemplate;

@RestController
public class OtelSpringStarterSmokeTestController {

  public static final String PING = "/ping";
  public static final String REST_CLIENT = "/rest-client";
  public static final String REST_TEMPLATE = "/rest-template";
  public static final String TEST_HISTOGRAM = "histogram-test-otel-spring-starter";
  public static final String METER_SCOPE_NAME = "scope";
  private final LongHistogram histogram;
  private final Optional<RestTemplate> restTemplate;
  private final Optional<ServletWebServerApplicationContext> server;

  public OtelSpringStarterSmokeTestController(
      OpenTelemetry openTelemetry,
      RestTemplateBuilder restTemplateBuilder,
      Optional<ServletWebServerApplicationContext> server) {
    this.server = server;
    Meter meter = openTelemetry.getMeter(METER_SCOPE_NAME);
    histogram = meter.histogramBuilder(TEST_HISTOGRAM).ofLongs().build();
    restTemplate = getRootUri().map(uri -> restTemplateBuilder.rootUri(uri).build());
  }

  public Optional<String> getRootUri() {
    return server.map(s -> "http://localhost:" + s.getWebServer().getPort());
  }

  @GetMapping(PING)
  public String ping() {
    histogram.record(10);
    return "pong";
  }

  @GetMapping(REST_TEMPLATE)
  public String restTemplate() {
    return restTemplate
        .map(t -> t.getForObject(PING, String.class))
        .orElseThrow(() -> new IllegalStateException("RestTemplate not available"));
  }
}
