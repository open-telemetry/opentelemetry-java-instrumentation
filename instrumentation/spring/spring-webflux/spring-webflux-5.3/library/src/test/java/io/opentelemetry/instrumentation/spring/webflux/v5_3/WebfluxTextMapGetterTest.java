/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webflux.v5_3;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

class WebfluxTextMapGetterTest {

  @Test
  void testGet() {
    MockServerHttpRequest request =
        MockServerHttpRequest.get("/test")
            .header("traceparent", "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01")
            .header("custom-header", "custom-value")
            .build();

    MockServerWebExchange exchange = MockServerWebExchange.from(request);

    String traceparent = WebfluxTextMapGetter.INSTANCE.get(exchange, "traceparent");
    assertThat(traceparent).isEqualTo("00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01");

    String customHeader = WebfluxTextMapGetter.INSTANCE.get(exchange, "custom-header");
    assertThat(customHeader).isEqualTo("custom-value");
  }

  @Test
  void testGetAll() {
    MockServerHttpRequest request =
        MockServerHttpRequest.get("/test")
            .header("accept", "application/json")
            .header("accept", "text/html")
            .build();

    MockServerWebExchange exchange = MockServerWebExchange.from(request);

    List<String> acceptHeaders = new ArrayList<>();
    WebfluxTextMapGetter.INSTANCE.getAll(exchange, "accept").forEachRemaining(acceptHeaders::add);

    assertThat(acceptHeaders).containsExactly("application/json", "text/html");
  }

  @Test
  void testKeysWithBaggageHeader() {
    MockServerHttpRequest request =
        MockServerHttpRequest.get("/test")
            .header("traceparent", "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01")
            .header("baggage", "test-baggage-key-1=test-baggage-value-1")
            .build();

    MockServerWebExchange exchange = MockServerWebExchange.from(request);

    Iterable<String> keys = WebfluxTextMapGetter.INSTANCE.keys(exchange);
    assertThat(keys).contains("traceparent", "baggage");

    String baggageValue = WebfluxTextMapGetter.INSTANCE.get(exchange, "baggage");
    assertThat(baggageValue).isEqualTo("test-baggage-key-1=test-baggage-value-1");
  }

  @Test
  void testKeysWithMultipleBaggageHeaders() {
    MockServerHttpRequest request =
        MockServerHttpRequest.get("/test")
            .header("traceparent", "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01")
            .header("baggage", "test-baggage-key-1=test-baggage-value-1")
            .header("baggage", "test-baggage-key-2=test-baggage-value-2")
            .header("x-custom", "custom-value")
            .build();

    MockServerWebExchange exchange = MockServerWebExchange.from(request);

    Iterable<String> keys = WebfluxTextMapGetter.INSTANCE.keys(exchange);
    assertThat(keys).contains("traceparent", "baggage", "x-custom");

    List<String> baggageValues = new ArrayList<>();
    WebfluxTextMapGetter.INSTANCE.getAll(exchange, "baggage").forEachRemaining(baggageValues::add);

    assertThat(baggageValues)
        .containsExactly(
            "test-baggage-key-1=test-baggage-value-1", "test-baggage-key-2=test-baggage-value-2");
  }
}
