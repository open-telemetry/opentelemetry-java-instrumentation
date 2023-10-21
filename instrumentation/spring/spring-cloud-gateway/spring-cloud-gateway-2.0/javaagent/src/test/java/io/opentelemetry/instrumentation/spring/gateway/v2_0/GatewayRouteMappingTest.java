/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.gateway.v2_0;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.semconv.SemanticAttributes;
import io.opentelemetry.testing.internal.armeria.client.WebClient;
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.embedded.netty.NettyReactiveWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = {
      GatewayTestApplication.class,
      GatewayRouteMappingTest.ForceNettyAutoConfiguration.class
    })
class GatewayRouteMappingTest {

  private static final AttributeKey<String> ROUTE_INFO_ATTRIBUTES =
      AttributeKey.stringKey("ROUTE_INFO");

  private static final String UNSET_ROUTE_ID = "UNSET_ROUTE_ID";

  @TestConfiguration
  static class ForceNettyAutoConfiguration {
    @Bean
    NettyReactiveWebServerFactory nettyFactory() {
      return new NettyReactiveWebServerFactory();
    }
  }

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Value("${local.server.port}")
  private int port;

  private WebClient client;

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
                span -> span.hasAttribute(equalTo(SemanticAttributes.HTTP_ROUTE, "path_route")),
                span ->
                    span.hasAttributesSatisfying(
                        satisfies(ROUTE_INFO_ATTRIBUTES, s -> s.contains("id='path_route'")),
                        satisfies(
                            ROUTE_INFO_ATTRIBUTES, s -> s.contains("uri=h1c://mock.response")))));
  }

  @Test
  void gatewayRandomUUIDRouteMappingTest() {
    String requestBody = "gateway";
    AggregatedHttpResponse response = client.post("/uuid/echo", requestBody).aggregate().join();
    assertThat(response.status().code()).isEqualTo(200);
    assertThat(response.contentUtf8()).isEqualTo(requestBody);
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasAttribute(equalTo(SemanticAttributes.HTTP_ROUTE, UNSET_ROUTE_ID)),
                span ->
                    span.hasAttributesSatisfying(
                        satisfies(ROUTE_INFO_ATTRIBUTES, s -> s.contains("uri=h1c://mock.uuid")))));
  }

  @Test
  void gatewayFakeUUIDRouteMappingTest() {
    String requestBody = "gateway";
    AggregatedHttpResponse response = client.post("/fake/echo", requestBody).aggregate().join();
    assertThat(response.status().code()).isEqualTo(200);
    assertThat(response.contentUtf8()).isEqualTo(requestBody);
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasAttribute(
                        equalTo(SemanticAttributes.HTTP_ROUTE, "ffffffff-ffff-ffff-ffff-ffff")),
                span ->
                    span.hasAttributesSatisfying(
                        satisfies(ROUTE_INFO_ATTRIBUTES, s -> s.contains("uri=h1c://mock.fake")))));
  }
}
