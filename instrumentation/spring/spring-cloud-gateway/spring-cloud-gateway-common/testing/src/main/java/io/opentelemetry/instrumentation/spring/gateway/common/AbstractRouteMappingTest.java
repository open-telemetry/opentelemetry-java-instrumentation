/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.gateway.common;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import io.opentelemetry.testing.internal.armeria.client.WebClient;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.embedded.netty.NettyReactiveWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;

public abstract class AbstractRouteMappingTest {
  @TestConfiguration
  public static class ForceNettyAutoConfiguration {
    @Bean
    NettyReactiveWebServerFactory nettyFactory() {
      return new NettyReactiveWebServerFactory();
    }
  }

  @RegisterExtension
  protected static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Value("${local.server.port}")
  private int port;

  protected WebClient client;

  protected static final String WEBFLUX_SPAN_NAME = "FilteringWebHandler.handle";

  @BeforeEach
  void beforeEach() {
    client = WebClient.builder("h1c://localhost:" + port).followRedirects().build();
  }

  protected List<AttributeAssertion> buildAttributeAssertions(
      String routeId, String uri, int order, int filterSize) {
    List<AttributeAssertion> assertions = new ArrayList<>();
    if (!StringUtils.isEmpty(routeId)) {
      assertions.add(equalTo(AttributeKey.stringKey("spring-cloud-gateway.route.id"), routeId));
    }
    assertions.add(equalTo(AttributeKey.stringKey("spring-cloud-gateway.route.uri"), uri));
    assertions.add(equalTo(AttributeKey.longKey("spring-cloud-gateway.route.order"), order));
    assertions.add(
        equalTo(AttributeKey.longKey("spring-cloud-gateway.route.filter.size"), filterSize));
    return assertions;
  }

  protected List<AttributeAssertion> buildAttributeAssertions(
      String uri, int order, int filterSize) {
    return buildAttributeAssertions(null, uri, order, filterSize);
  }
}
