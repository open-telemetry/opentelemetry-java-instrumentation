/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.gateway.v2_0;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.spring.gateway.common.AbstractRouteMappingTest;
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = {
      GatewayTestApplication.class,
      GatewayRouteMappingTest.ForceNettyAutoConfiguration.class
    })
class GatewayRouteMappingTest extends AbstractRouteMappingTest {

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
                    span.hasName("POST path_route")
                        .hasKind(SpanKind.SERVER)
                        .hasAttributesSatisfying(
                            buildAttributeAssertions("path_route", "h1c://mock.response", 0, 1)),
                span -> span.hasName(WEBFLUX_SPAN_NAME).hasKind(SpanKind.INTERNAL)));
  }

  @Test
  void gatewayRandomUuidRouteMappingTest() {
    String requestBody = "gateway";
    AggregatedHttpResponse response = client.post("/uuid/echo", requestBody).aggregate().join();
    assertThat(response.status().code()).isEqualTo(200);
    assertThat(response.contentUtf8()).isEqualTo(requestBody);
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("POST")
                        .hasKind(SpanKind.SERVER)
                        .hasAttributesSatisfying(buildAttributeAssertions("h1c://mock.uuid", 0, 1)),
                span -> span.hasName(WEBFLUX_SPAN_NAME).hasKind(SpanKind.INTERNAL)));
  }

  @Test
  void gatewayFakeUuidRouteMappingTest() {
    String requestBody = "gateway";
    String routeId = "ffffffff-ffff-ffff-ffff-ffff";
    AggregatedHttpResponse response = client.post("/fake/echo", requestBody).aggregate().join();
    assertThat(response.status().code()).isEqualTo(200);
    assertThat(response.contentUtf8()).isEqualTo(requestBody);
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("POST " + routeId)
                        .hasKind(SpanKind.SERVER)
                        .hasAttributesSatisfying(
                            buildAttributeAssertions(routeId, "h1c://mock.fake", 0, 1)),
                span -> span.hasName(WEBFLUX_SPAN_NAME).hasKind(SpanKind.INTERNAL)));
  }
}
