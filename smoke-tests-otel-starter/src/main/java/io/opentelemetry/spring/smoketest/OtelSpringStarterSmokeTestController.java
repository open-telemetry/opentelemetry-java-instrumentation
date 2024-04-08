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

  public static final String URL = "/ping";
  public static final String TEST_HISTOGRAM = "histogram-test-otel-spring-starter";
  private final LongHistogram histogram;
  private final Optional<RestTemplate> restTemplate;

  public OtelSpringStarterSmokeTestController(
      OpenTelemetry openTelemetry,
      RestTemplateBuilder restTemplateBuilder,
      Optional<ServletWebServerApplicationContext> server) {
    Meter meter = openTelemetry.getMeter(OtelSpringStarterSmokeTestApplication.class.getName());
    histogram = meter.histogramBuilder(TEST_HISTOGRAM).ofLongs().build();
    restTemplate =
        server.map(
            s ->
                restTemplateBuilder
                    .rootUri("http://localhost:" + s.getWebServer().getPort())
                    .build());
  }

  @GetMapping(URL)
  public String ping() {
    histogram.record(10);
    return restTemplate
        .map(t -> t.getForObject("/pong", String.class))
        .orElseThrow(() -> new IllegalStateException("RestTemplate not available"));
  }

  @GetMapping("/pong")
  public String pong() {
    return "pong";
  }
}
