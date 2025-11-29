/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.spring.smoketest;

import java.net.URI;
import java.util.List;
import org.assertj.core.api.AbstractIterableAssert;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
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
      "otel.instrumentation.runtime-telemetry-java17.enable-all=true",
      "otel.instrumentation.common.thread_details.enabled=true",
      "logging.level.org.springframework.boot.autoconfigure=DEBUG",
    })
@AutoConfigureTestRestTemplate
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
    if (!isFlightRecorderAvailable()) {
      return;
    }

    // JFR based metrics
    for (String metric :
        List.of(
            "jvm.cpu.limit",
            "jvm.buffer.count",
            "jvm.class.count",
            "jvm.cpu.context_switch",
            "jvm.system.cpu.utilization",
            "jvm.gc.duration",
            "jvm.memory.init",
            "jvm.memory.used",
            "jvm.memory.allocation",
            "jvm.network.io",
            "jvm.thread.count")) {
      testing.waitAndAssertMetrics(
          "io.opentelemetry.runtime-telemetry-java17", metric, AbstractIterableAssert::isNotEmpty);
    }
  }

  private static boolean isFlightRecorderAvailable() {
    try {
      return (boolean)
          Class.forName("jdk.jfr.FlightRecorder").getMethod("isAvailable").invoke(null);
    } catch (ReflectiveOperationException exception) {
      return false;
    }
  }
}
