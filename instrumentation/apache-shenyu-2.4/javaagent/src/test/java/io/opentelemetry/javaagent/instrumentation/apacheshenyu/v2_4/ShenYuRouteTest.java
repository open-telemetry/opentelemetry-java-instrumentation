/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apacheshenyu.v2_4;

import static io.opentelemetry.api.common.AttributeKey.booleanKey;
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

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.apache.shenyu.common.dto.MetaData;
import org.assertj.core.api.AbstractLongAssert;
import org.assertj.core.api.AbstractStringAssert;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import reactor.netty.http.client.HttpClient;

@ExtendWith(SpringExtension.class)
@SpringBootTest(
    properties = {"shenyu.local.enabled=true", "spring.main.allow-bean-definition-overriding=true"},
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = {ShenYuBootstrapApplication.class})
class ShenYuRouteTest {

  @Value("${local.server.port}")
  private int port;

  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @BeforeAll
  static void beforeAll()
      throws ClassNotFoundException,
          NoSuchMethodException,
          InvocationTargetException,
          IllegalAccessException {

    Class<?> metaDataCache;
    try {
      metaDataCache = Class.forName("org.apache.shenyu.plugin.global.cache.MetaDataCache");
    } catch (ClassNotFoundException e) {
      // in 2.5.0, the MetaDataCache turned to be org.apache.shenyu.plugin.base.cache
      metaDataCache = Class.forName("org.apache.shenyu.plugin.base.cache.MetaDataCache");
    }

    Object cacheInst = metaDataCache.getMethod("getInstance").invoke(null);
    Method cacheMethod = metaDataCache.getMethod("cache", MetaData.class);

    cacheMethod.invoke(
        cacheInst,
        MetaData.builder()
            .id("123")
            .appName("test-shenyu")
            .contextPath("/")
            .path("/a/b/c")
            .rpcType("http")
            .serviceName("shenyu-service")
            .methodName("hello")
            .parameterTypes("string")
            .rpcExt("test-ext")
            .enabled(true)
            .build());
  }

  @Test
  void testUpdateRouter() {
    HttpClient httpClient = HttpClient.create();
    httpClient.get().uri("http://localhost:" + port + "/a/b/c").response().block();
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("GET").hasKind(SpanKind.CLIENT),
                span ->
                    span.hasName("GET /a/b/c")
                        .hasKind(SpanKind.SERVER)
                        .hasAttributesSatisfyingExactly(
                            equalTo(HTTP_ROUTE, "/a/b/c"),
                            equalTo(HTTP_REQUEST_METHOD, "GET"),
                            equalTo(HTTP_RESPONSE_STATUS_CODE, 200),
                            equalTo(URL_PATH, "/a/b/c"),
                            equalTo(URL_SCHEME, "http"),
                            satisfies(CLIENT_ADDRESS, AbstractStringAssert::isNotBlank),
                            satisfies(NETWORK_PEER_ADDRESS, AbstractStringAssert::isNotBlank),
                            satisfies(NETWORK_PEER_PORT, AbstractLongAssert::isPositive),
                            satisfies(NETWORK_PROTOCOL_VERSION, AbstractStringAssert::isNotBlank),
                            satisfies(SERVER_ADDRESS, AbstractStringAssert::isNotBlank),
                            satisfies(SERVER_PORT, AbstractLongAssert::isPositive),
                            satisfies(USER_AGENT_ORIGINAL, AbstractStringAssert::isNotBlank),
                            equalTo(stringKey("apache-shenyu.meta.id"), "123"),
                            equalTo(booleanKey("apache-shenyu.meta.enabled"), true),
                            equalTo(stringKey("apache-shenyu.meta.method-name"), "hello"),
                            equalTo(stringKey("apache-shenyu.meta.param-types"), "string"),
                            equalTo(stringKey("apache-shenyu.meta.path"), "/a/b/c"),
                            equalTo(stringKey("apache-shenyu.meta.rpc-ext"), "test-ext"),
                            equalTo(stringKey("apache-shenyu.meta.rpc-type"), "http"),
                            equalTo(stringKey("apache-shenyu.meta.service-name"), "shenyu-service"),
                            equalTo(stringKey("apache-shenyu.meta.app-name"), "test-shenyu"),
                            equalTo(stringKey("apache-shenyu.meta.context-path"), "/"))));
  }

  @Test
  void testUnmatchedRouter() {
    HttpClient httpClient = HttpClient.create();
    httpClient.get().uri("http://localhost:" + port + "/a/b/c/d").response().block();
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("GET").hasKind(SpanKind.CLIENT),
                span -> span.hasName("GET").hasKind(SpanKind.SERVER)));
  }
}
