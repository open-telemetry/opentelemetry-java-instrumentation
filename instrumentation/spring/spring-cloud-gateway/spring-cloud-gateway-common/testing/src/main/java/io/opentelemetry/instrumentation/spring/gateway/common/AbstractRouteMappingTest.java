/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.gateway.common;

import static io.opentelemetry.api.common.AttributeKey.longKey;
import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import io.opentelemetry.testing.internal.armeria.client.WebClient;
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpResponse;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;

public abstract class AbstractRouteMappingTest {
  @RegisterExtension
  protected static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Value("${local.server.port}")
  private int port;

  protected WebClient client;

  protected static final String WEBFLUX_SPAN_NAME = "FilteringWebHandler.handle";

  protected String getSpanName() {
    return "POST";
  }

  protected String getInternalSpanName() {
    return WEBFLUX_SPAN_NAME;
  }

  protected List<AttributeAssertion> getExpectedAttributes() {
    // Global filter is not route filter, so filter size should be 0.
    return buildAttributeAssertions("h1c://mock.response", 2023, 0);
  }

  @BeforeEach
  void beforeEach() {
    client = WebClient.builder("h1c://localhost:" + port).followRedirects().build();
  }

  @Test
  void gatewayRouteMappingTest() {
    String requestBody = "gateway";
    AggregatedHttpResponse response = client.post("/gateway/echo", requestBody).aggregate().join();
    assertThat(response.status().code()).isEqualTo(200);
    assertThat(response.contentUtf8()).isEqualTo(requestBody);
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(getSpanName())
                        .hasKind(SpanKind.SERVER)
                        .hasAttributesSatisfying(getExpectedAttributes()),
                span -> span.hasName(getInternalSpanName()).hasKind(SpanKind.INTERNAL)));
  }

  protected List<AttributeAssertion> buildAttributeAssertions(
      @Nullable String routeId, String uri, int order, int filterSize) {
    List<AttributeAssertion> assertions = new ArrayList<>();
    if (!StringUtils.isEmpty(routeId)) {
      assertions.add(equalTo(stringKey("spring-cloud-gateway.route.id"), routeId));
    }
    assertions.add(equalTo(stringKey("spring-cloud-gateway.route.uri"), uri));
    assertions.add(equalTo(longKey("spring-cloud-gateway.route.order"), order));
    assertions.add(equalTo(longKey("spring-cloud-gateway.route.filter.size"), filterSize));
    return assertions;
  }

  protected List<AttributeAssertion> buildAttributeAssertions(
      String uri, int order, int filterSize) {
    return buildAttributeAssertions(null, uri, order, filterSize);
  }
}
