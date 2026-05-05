/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.gateway.common;

import static io.opentelemetry.api.common.AttributeKey.longKey;
import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.ClientAttributes.CLIENT_ADDRESS;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_REQUEST_METHOD;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_RESPONSE_STATUS_CODE;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_ROUTE;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PROTOCOL_VERSION;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.UrlAttributes.URL_PATH;
import static io.opentelemetry.semconv.UrlAttributes.URL_SCHEME;
import static io.opentelemetry.semconv.UserAgentAttributes.USER_AGENT_ORIGINAL;
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
import org.assertj.core.api.AbstractLongAssert;
import org.assertj.core.api.AbstractStringAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;

public abstract class AbstractRouteMappingTest {
  private static final String UUID_REGEX =
      "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

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

  @Nullable
  protected String getHttpRoute() {
    return "/gateway/echo";
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
  protected void testGatewayRouteMapping() {
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
                        .hasAttributesSatisfyingExactly(
                            withHttpServerAttributes(
                                getExpectedAttributes(), getHttpRoute(), "/gateway/echo")),
                span -> span.hasName(getInternalSpanName()).hasKind(SpanKind.INTERNAL)));
  }

  @Test
  protected void testRandomUuidRouteFiltering() {
    String requestBody = "gateway";
    AggregatedHttpResponse response = client.post("/uuid/echo", requestBody).aggregate().join();
    assertThat(response.status().code()).isEqualTo(200);
    assertThat(response.contentUtf8()).isEqualTo(requestBody);
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(getRandomUuidSpanName())
                        .hasKind(SpanKind.SERVER)
                        .hasAttributesSatisfyingExactly(
                            withHttpServerAttributes(
                                getRandomUuidExpectedAttributes(),
                                getRandomUuidHttpRoute(),
                                "/uuid/echo")),
                span -> span.hasName(getInternalSpanName()).hasKind(SpanKind.INTERNAL)));
  }

  @Test
  protected void testFakeUuidRouteNotFiltered() {
    String requestBody = "gateway";
    String routeId = "ffffffff-ffff-ffff-ffff-ffff";
    AggregatedHttpResponse response = client.post("/fake/echo", requestBody).aggregate().join();
    assertThat(response.status().code()).isEqualTo(200);
    assertThat(response.contentUtf8()).isEqualTo(requestBody);
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(getFakeUuidSpanName(routeId))
                        .hasKind(SpanKind.SERVER)
                        .hasAttributesSatisfyingExactly(
                            withHttpServerAttributes(
                                getFakeUuidExpectedAttributes(routeId),
                                getFakeUuidHttpRoute(routeId),
                                "/fake/echo")),
                span -> span.hasName(getInternalSpanName()).hasKind(SpanKind.INTERNAL)));
  }

  protected String getRandomUuidSpanName() {
    return "POST";
  }

  @Nullable
  protected String getRandomUuidHttpRoute() {
    return "/uuid/echo";
  }

  protected List<AttributeAssertion> getRandomUuidExpectedAttributes() {
    return buildAttributeAssertions("h1c://mock.uuid", 0, 0);
  }

  protected String getFakeUuidSpanName(String routeId) {
    return "POST " + routeId;
  }

  @Nullable
  protected String getFakeUuidHttpRoute(String routeId) {
    return "/fake/echo";
  }

  protected List<AttributeAssertion> getFakeUuidExpectedAttributes(String routeId) {
    return buildAttributeAssertions(routeId, "h1c://mock.fake", 0, 0);
  }

  protected List<AttributeAssertion> buildAttributeAssertions(
      @Nullable String routeId, String uri, int order, int filterSize) {
    List<AttributeAssertion> assertions = new ArrayList<>();
    if (routeId == null) {
      assertions.add(
          satisfies(stringKey("spring-cloud-gateway.route.id"), val -> val.matches(UUID_REGEX)));
    } else if (!StringUtils.isEmpty(routeId)) {
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

  private static List<AttributeAssertion> withHttpServerAttributes(
      List<AttributeAssertion> routeAssertions, @Nullable String httpRoute, String urlPath) {
    List<AttributeAssertion> assertions = new ArrayList<>(routeAssertions);
    if (httpRoute != null) {
      assertions.add(equalTo(HTTP_ROUTE, httpRoute));
    }
    assertions.add(equalTo(HTTP_REQUEST_METHOD, "POST"));
    assertions.add(equalTo(HTTP_RESPONSE_STATUS_CODE, 200));
    assertions.add(equalTo(URL_PATH, urlPath));
    assertions.add(equalTo(URL_SCHEME, "http"));
    assertions.add(satisfies(CLIENT_ADDRESS, AbstractStringAssert::isNotBlank));
    assertions.add(satisfies(NETWORK_PEER_ADDRESS, AbstractStringAssert::isNotBlank));
    assertions.add(satisfies(NETWORK_PEER_PORT, AbstractLongAssert::isPositive));
    assertions.add(satisfies(NETWORK_PROTOCOL_VERSION, AbstractStringAssert::isNotBlank));
    assertions.add(satisfies(SERVER_ADDRESS, AbstractStringAssert::isNotBlank));
    assertions.add(satisfies(SERVER_PORT, AbstractLongAssert::isPositive));
    assertions.add(satisfies(USER_AGENT_ORIGINAL, AbstractStringAssert::isNotBlank));
    return assertions;
  }
}
