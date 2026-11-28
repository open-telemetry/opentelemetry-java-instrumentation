/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webflux.v5_3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.web.reactive.HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.util.pattern.PathPatternParser;

class WebfluxServerHttpAttributesGetterTest {

  @Test
  void routeLookup() {
    MockServerWebExchange exchange =
        MockServerWebExchange.from(MockServerHttpRequest.get("/app/items/123").contextPath("/app"));
    WebfluxServerHttpAttributesGetter getter = new WebfluxServerHttpAttributesGetter();

    assertThat(getter.getHttpRoute(exchange)).isNull();
    assertThat(WebfluxServerHttpAttributesGetter.getRoute(exchange)).isNull();

    exchange.getAttributes().put(BEST_MATCHING_PATTERN_ATTRIBUTE, "/**");
    assertThat(getter.getHttpRoute(exchange)).isNull();
    assertThat(WebfluxServerHttpAttributesGetter.getRoute(exchange)).isNull();

    exchange.getAttributes().put(BEST_MATCHING_PATTERN_ATTRIBUTE, "items/{id}");
    assertThat(getter.getHttpRoute(exchange)).isEqualTo("/app/items/{id}");
    assertThat(WebfluxServerHttpAttributesGetter.getRoute(exchange)).isEqualTo("/app/items/{id}");

    exchange
        .getAttributes()
        .put(BEST_MATCHING_PATTERN_ATTRIBUTE, new PathPatternParser().parse("/items/{id}"));
    assertThat(getter.getHttpRoute(exchange)).isEqualTo("/app/items/{id}");
    assertThat(WebfluxServerHttpAttributesGetter.getRoute(exchange)).isEqualTo("/app/items/{id}");
  }
}
