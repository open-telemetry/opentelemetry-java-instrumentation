/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.gateway.v2_2;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.spring.gateway.common.AbstractRouteMappingTest;
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = {
      Gateway22TestApplication.class,
      Gateway22RouteMappingTest.ForceNettyAutoConfiguration.class
    })
class Gateway22RouteMappingTest extends AbstractRouteMappingTest {

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
                    span.hasName("POST")
                        .hasKind(SpanKind.SERVER)
                        .hasAttributesSatisfying(
                            // Global filter is not route filter, so filter size should be 0.
                            buildAttributeAssertions("h1c://mock.response", 2023, 0)),
                span -> span.hasName(WEBFLUX_SPAN_NAME).hasKind(SpanKind.INTERNAL)));
  }
}
