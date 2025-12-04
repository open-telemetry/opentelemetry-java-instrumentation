/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.gateway.webmvc.v5_0;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.spring.gateway.common.AbstractRouteMappingTest;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = {Gateway43MvcTestApplication.class})
class Gateway43MvcRouteMappingTest extends AbstractRouteMappingTest {

  @Test
  void gatewayRouteMappingTest() {
    testGatewayRouteMapping();
  }

  @Override
  protected String getSpanName() {
    return "POST /gateway/echo";
  }

  @Override
  protected String getInternalSpanName() {
    // WebMVC creates an internal span for the handler filter function
    return "HandlerFilterFunction$$Lambda.<annotation>";
  }

  /**
   * WebMVC variant only has access to route ID at the point of instrumentation. Route URI is set by
   * filters that execute later and is not available when the route method is called. Order and
   * filter size are not available in request attributes.
   */
  @Override
  protected List<AttributeAssertion> getExpectedAttributes() {
    List<AttributeAssertion> assertions = new ArrayList<>();
    assertions.add(
        equalTo(AttributeKey.stringKey("spring-cloud-gateway.route.id"), "test-route-id"));
    return assertions;
  }
}
