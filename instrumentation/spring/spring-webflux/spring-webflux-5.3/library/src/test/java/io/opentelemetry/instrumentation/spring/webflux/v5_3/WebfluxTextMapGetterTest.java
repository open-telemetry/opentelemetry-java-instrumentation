/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webflux.v5_3;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

class WebfluxTextMapGetterTest {

  @Test
  void testKeysWithMultipleHeaders() {
    MockServerHttpRequest request =
        MockServerHttpRequest.get("/test")
            .header("traceparent", "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01")
            .header("tracestate", "congo=t61rcWkgMzE")
            .header("custom-header", "custom-value")
            .header("x-forwarded-for", "192.168.1.1")
            .build();

    MockServerWebExchange exchange = MockServerWebExchange.from(request);

    Iterable<String> keys = WebfluxTextMapGetter.INSTANCE.keys(exchange);
    assertThat(keys).contains("traceparent", "tracestate", "custom-header", "x-forwarded-for");
  }

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
  void testGetAllSingleValue() {
    MockServerHttpRequest request =
        MockServerHttpRequest.get("/test").header("content-type", "application/json").build();

    MockServerWebExchange exchange = MockServerWebExchange.from(request);

    List<String> contentTypes = new ArrayList<>();
    WebfluxTextMapGetter.INSTANCE
        .getAll(exchange, "content-type")
        .forEachRemaining(contentTypes::add);

    assertThat(contentTypes).containsExactly("application/json");
  }

  @Test
  void testGetNullExchange() {
    String result = WebfluxTextMapGetter.INSTANCE.get(null, "any-header");
    assertThat(result).isNull();
  }

  @Test
  void testKeysDirectlyOnHttpHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.add("traceparent", "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01");
    headers.add("tracestate", "congo=t61rcWkgMzE");
    headers.add("custom-header", "custom-value");

    MockServerHttpRequest request = MockServerHttpRequest.get("/test").headers(headers).build();
    ServerWebExchange exchange = MockServerWebExchange.from(request);

    // The keys() method internally calls HttpHeaders.keySet()
    // This will throw NoSuchMethodError with Spring Web 7 if not properly handled
    Iterable<String> keys = WebfluxTextMapGetter.INSTANCE.keys(exchange);
    assertThat(keys).hasSize(3).contains("traceparent", "tracestate", "custom-header");
  }

  @Test
  void testGetAllNullExchange() {
    assertThat(WebfluxTextMapGetter.INSTANCE.getAll(null, "any-header")).isExhausted();
  }

  @Test
  void testKeysWithBaggageHeader() {
    // Test that baggage headers are properly returned by keys()
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
    // Test that multiple baggage headers are properly returned by keys()
    // The W3C Baggage propagator needs to iterate through all headers to find baggage entries
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
