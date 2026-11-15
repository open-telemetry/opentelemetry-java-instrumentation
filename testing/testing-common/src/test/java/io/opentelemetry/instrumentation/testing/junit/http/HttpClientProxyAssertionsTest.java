/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.junit.http;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_REQUEST_METHOD;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_REQUEST_METHOD_ORIGINAL;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_RESPONSE_STATUS_CODE;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PROTOCOL_VERSION;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.UrlAttributes.URL_FULL;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.incubator.ExtendedOpenTelemetry;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.api.incubator.semconv.service.internal.ServiceMappingResolver;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.testing.trace.TestSpanData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.semconv.SchemaUrls;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junitpioneer.jupiter.SetSystemProperty;

class HttpClientProxyAssertionsTest {

  private static final URI TARGET_URI = AbstractHttpClientTest.PROXY_TARGET_URI;

  @ParameterizedTest
  @MethodSource("acceptedPeers")
  void acceptsExpectedPeerAttributes(boolean expectPeer, Attributes peerAttributes)
      throws UnknownHostException {
    assertProxySpan(expectPeer, peerAttributes);
  }

  @ParameterizedTest
  @MethodSource("acceptedPeers")
  @SetSystemProperty(
      key = "otel.instrumentation.common.server-service-mapping",
      value = "192.0.2.1=test-server-service")
  void acceptsExpectedPeerAttributesWithServerServiceMapping(
      boolean expectPeer, Attributes peerAttributes) throws UnknownHostException {
    DeclarativeConfigProperties mapping = mock(DeclarativeConfigProperties.class);
    when(mapping.getString("target")).thenReturn("192.0.2.1");
    when(mapping.getString("service_name")).thenReturn("test-server-service");

    DeclarativeConfigProperties commonConfig = mock(DeclarativeConfigProperties.class);
    when(commonConfig.getStructuredList("server_service_mapping", emptyList()))
        .thenReturn(singletonList(mapping));

    ExtendedOpenTelemetry openTelemetry = mock(ExtendedOpenTelemetry.class);
    when(openTelemetry.getInstrumentationConfig("common")).thenReturn(commonConfig);

    AttributesBuilder attributes = peerAttributes.toBuilder();
    new ServiceMappingResolver(openTelemetry)
        .resolve(TARGET_URI.getHost(), TARGET_URI.getPort(), TARGET_URI::getPath, attributes::put);

    assertProxySpan(expectPeer, attributes.build());
  }

  @ParameterizedTest
  @MethodSource("rejectedPeers")
  void rejectsIncorrectPeerAttributes(boolean expectPeer, Attributes peerAttributes) {
    assertThatThrownBy(() -> assertProxySpan(expectPeer, peerAttributes))
        .isInstanceOf(AssertionError.class);
  }

  @ParameterizedTest
  @MethodSource("acceptedPeers")
  void rejectsUnexpectedAttributes(boolean expectPeer, Attributes peerAttributes) {
    Attributes attributes =
        peerAttributes.toBuilder().put(HTTP_REQUEST_METHOD_ORIGINAL, "GET").build();

    assertThatThrownBy(() -> assertProxySpan(expectPeer, attributes))
        .isInstanceOf(AssertionError.class);
  }

  private static Stream<Arguments> acceptedPeers() {
    return Stream.of(
        argumentSet(
            "proxy address and port",
            true,
            Attributes.of(NETWORK_PEER_ADDRESS, "127.0.0.1", NETWORK_PEER_PORT, 43127L)),
        argumentSet("peer attributes omitted", false, Attributes.empty()));
  }

  private static Stream<Arguments> rejectedPeers() {
    return Stream.of(
        argumentSet("missing expected peer", true, Attributes.empty()),
        argumentSet(
            "destination address reported as peer",
            true,
            Attributes.of(NETWORK_PEER_ADDRESS, TARGET_URI.getHost(), NETWORK_PEER_PORT, 43127L)),
        argumentSet(
            "destination port reported as peer",
            true,
            Attributes.of(
                NETWORK_PEER_ADDRESS, "127.0.0.1", NETWORK_PEER_PORT, (long) TARGET_URI.getPort())),
        argumentSet(
            "missing expected peer port", true, Attributes.of(NETWORK_PEER_ADDRESS, "127.0.0.1")),
        argumentSet(
            "missing expected peer address", true, Attributes.of(NETWORK_PEER_PORT, 43127L)),
        argumentSet(
            "unexpected destination peer",
            false,
            Attributes.of(
                NETWORK_PEER_ADDRESS,
                TARGET_URI.getHost(),
                NETWORK_PEER_PORT,
                (long) TARGET_URI.getPort())),
        argumentSet(
            "unexpected peer address", false, Attributes.of(NETWORK_PEER_ADDRESS, "127.0.0.1")),
        argumentSet("unexpected peer port", false, Attributes.of(NETWORK_PEER_PORT, 43127L)));
  }

  private static void assertProxySpan(boolean expectPeer, Attributes additionalAttributes)
      throws UnknownHostException {
    TestClient client = new TestClient(expectPeer);
    client.setupOptions();
    InetSocketAddress proxyAddress =
        new InetSocketAddress(InetAddress.getByAddress(new byte[] {127, 0, 0, 1}), 43127);

    Attributes attributes =
        Attributes.builder()
            .put(HTTP_REQUEST_METHOD, "GET")
            .put(HTTP_RESPONSE_STATUS_CODE, 200L)
            .put(NETWORK_PROTOCOL_VERSION, "1.1")
            .put(SERVER_ADDRESS, TARGET_URI.getHost())
            .put(SERVER_PORT, (long) TARGET_URI.getPort())
            .put(URL_FULL, TARGET_URI.toString())
            .putAll(additionalAttributes)
            .build();

    SpanData span =
        TestSpanData.builder()
            .setName("GET")
            .setSpanContext(SpanContext.getInvalid())
            .setKind(SpanKind.CLIENT)
            .setStatus(StatusData.unset())
            .setHasEnded(true)
            .setStartEpochNanos(0)
            .setEndEpochNanos(1)
            .setInstrumentationScopeInfo(
                InstrumentationScopeInfo.builder("test").setSchemaUrl(SchemaUrls.V1_41_0).build())
            .setAttributes(attributes)
            .build();

    client.assertProxyClientSpan(assertThat(span), TARGET_URI, "GET", 200, proxyAddress);
  }

  private static final class TestClient extends AbstractHttpClientTest<Object> {

    private final boolean expectPeer;

    private TestClient(boolean expectPeer) {
      this.expectPeer = expectPeer;
    }

    @Override
    protected void configure(HttpClientTestOptions.Builder optionsBuilder) {
      optionsBuilder.setTestProxy(true).setExpectProxyNetworkPeerAttributes(expectPeer);
    }

    @Override
    public Object buildRequest(String method, URI uri, Map<String, String> headers) {
      throw new UnsupportedOperationException();
    }

    @Override
    public int sendRequest(Object request, String method, URI uri, Map<String, String> headers) {
      throw new UnsupportedOperationException();
    }
  }
}
