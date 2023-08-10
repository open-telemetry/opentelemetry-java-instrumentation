/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.http;

import static io.opentelemetry.instrumentation.api.instrumenter.http.TemporaryMetricsView.applyActiveRequestsView;
import static io.opentelemetry.instrumentation.api.instrumenter.http.TemporaryMetricsView.applyClientDurationAndSizeView;
import static io.opentelemetry.instrumentation.api.instrumenter.http.TemporaryMetricsView.applyServerDurationAndSizeView;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.NetTransportValues.IP_TCP;
import static org.assertj.core.api.Assertions.entry;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.instrumentation.api.instrumenter.http.internal.HttpAttributes;
import io.opentelemetry.instrumentation.api.instrumenter.network.internal.NetworkAttributes;
import io.opentelemetry.instrumentation.api.instrumenter.url.internal.UrlAttributes;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import org.junit.jupiter.api.Test;

class TemporaryMetricsViewTest {

  @Test
  void shouldApplyClientDurationAndSizeView() {
    Attributes startAttributes =
        Attributes.builder()
            .put(
                SemanticAttributes.HTTP_URL,
                "https://somehost/high/cardinality/12345?jsessionId=121454")
            .put(SemanticAttributes.HTTP_METHOD, "GET")
            .put(SemanticAttributes.HTTP_SCHEME, "https")
            .put(SemanticAttributes.HTTP_TARGET, "/high/cardinality/12345?jsessionId=121454")
            .build();

    Attributes endAttributes =
        Attributes.builder()
            .put(SemanticAttributes.HTTP_STATUS_CODE, 500)
            .put(SemanticAttributes.NET_TRANSPORT, IP_TCP)
            .put(SemanticAttributes.NET_PROTOCOL_NAME, "http")
            .put(SemanticAttributes.NET_PROTOCOL_VERSION, "1.1")
            .put(SemanticAttributes.NET_PEER_NAME, "somehost2")
            .put(SemanticAttributes.NET_PEER_PORT, 443)
            .put(SemanticAttributes.NET_SOCK_FAMILY, "inet")
            .put(SemanticAttributes.NET_SOCK_PEER_ADDR, "1.2.3.4")
            .put(SemanticAttributes.NET_SOCK_PEER_NAME, "somehost20")
            .put(SemanticAttributes.NET_SOCK_PEER_PORT, 8080)
            .build();

    assertThat(applyClientDurationAndSizeView(startAttributes, endAttributes))
        .containsOnly(
            entry(SemanticAttributes.HTTP_METHOD, "GET"),
            entry(SemanticAttributes.HTTP_STATUS_CODE, 500L),
            entry(SemanticAttributes.NET_PROTOCOL_NAME, "http"),
            entry(SemanticAttributes.NET_PROTOCOL_VERSION, "1.1"),
            entry(SemanticAttributes.NET_PEER_NAME, "somehost2"),
            entry(SemanticAttributes.NET_PEER_PORT, 443L),
            entry(SemanticAttributes.NET_SOCK_PEER_ADDR, "1.2.3.4"));
  }

  @Test
  void shouldApplyClientDurationAndSizeView_stableSemconv() {
    Attributes startAttributes =
        Attributes.builder()
            .put(
                UrlAttributes.URL_FULL, "https://somehost/high/cardinality/12345?jsessionId=121454")
            .put(HttpAttributes.HTTP_REQUEST_METHOD, "GET")
            .put(UrlAttributes.URL_SCHEME, "https")
            .put(UrlAttributes.URL_PATH, "/high/cardinality/12345")
            .put(UrlAttributes.URL_QUERY, "jsessionId=121454")
            .put(NetworkAttributes.SERVER_ADDRESS, "somehost2")
            .put(NetworkAttributes.SERVER_PORT, 443)
            .build();

    Attributes endAttributes =
        Attributes.builder()
            .put(HttpAttributes.HTTP_RESPONSE_STATUS_CODE, 500)
            .put(NetworkAttributes.NETWORK_TRANSPORT, "tcp")
            .put(NetworkAttributes.NETWORK_TYPE, "ipv4")
            .put(NetworkAttributes.NETWORK_PROTOCOL_NAME, "http")
            .put(NetworkAttributes.NETWORK_PROTOCOL_VERSION, "1.1")
            .put(NetworkAttributes.SERVER_SOCKET_ADDRESS, "1.2.3.4")
            .put(NetworkAttributes.SERVER_SOCKET_DOMAIN, "somehost20")
            .put(NetworkAttributes.SERVER_SOCKET_PORT, 8080)
            .build();

    assertThat(applyClientDurationAndSizeView(startAttributes, endAttributes))
        .containsOnly(
            entry(HttpAttributes.HTTP_REQUEST_METHOD, "GET"),
            entry(HttpAttributes.HTTP_RESPONSE_STATUS_CODE, 500L),
            entry(NetworkAttributes.NETWORK_PROTOCOL_NAME, "http"),
            entry(NetworkAttributes.NETWORK_PROTOCOL_VERSION, "1.1"),
            entry(NetworkAttributes.SERVER_ADDRESS, "somehost2"),
            entry(NetworkAttributes.SERVER_PORT, 443L),
            entry(NetworkAttributes.SERVER_SOCKET_ADDRESS, "1.2.3.4"));
  }

  @Test
  void shouldApplyServerDurationAndSizeView() {
    Attributes startAttributes =
        Attributes.builder()
            .put(SemanticAttributes.HTTP_METHOD, "GET")
            .put(
                SemanticAttributes.HTTP_URL,
                "https://somehost/high/cardinality/12345?jsessionId=121454")
            .put(SemanticAttributes.HTTP_TARGET, "/high/cardinality/12345?jsessionId=121454")
            .put(SemanticAttributes.HTTP_SCHEME, "https")
            .put(SemanticAttributes.NET_TRANSPORT, IP_TCP)
            .put(SemanticAttributes.NET_PROTOCOL_NAME, "http")
            .put(SemanticAttributes.NET_PROTOCOL_VERSION, "1.1")
            .put(SemanticAttributes.NET_HOST_NAME, "somehost")
            .put(SemanticAttributes.NET_HOST_PORT, 443)
            .put(SemanticAttributes.NET_SOCK_FAMILY, "inet")
            .put(SemanticAttributes.NET_SOCK_PEER_ADDR, "1.2.3.4")
            .put(SemanticAttributes.NET_SOCK_PEER_PORT, 8080)
            .put(SemanticAttributes.NET_SOCK_HOST_ADDR, "4.3.2.1")
            .put(SemanticAttributes.NET_SOCK_HOST_PORT, 9090)
            .build();

    Attributes endAttributes =
        Attributes.builder()
            .put(SemanticAttributes.HTTP_ROUTE, "/somehost/high/{name}/{id}")
            .put(SemanticAttributes.HTTP_STATUS_CODE, 500)
            .put(SemanticAttributes.NET_PEER_NAME, "somehost2")
            .put(SemanticAttributes.NET_PEER_PORT, 443)
            .build();

    assertThat(applyServerDurationAndSizeView(startAttributes, endAttributes))
        .containsOnly(
            entry(SemanticAttributes.HTTP_METHOD, "GET"),
            entry(SemanticAttributes.HTTP_STATUS_CODE, 500L),
            entry(SemanticAttributes.HTTP_SCHEME, "https"),
            entry(SemanticAttributes.NET_PROTOCOL_NAME, "http"),
            entry(SemanticAttributes.NET_PROTOCOL_VERSION, "1.1"),
            entry(SemanticAttributes.NET_HOST_NAME, "somehost"),
            entry(SemanticAttributes.NET_HOST_PORT, 443L),
            entry(SemanticAttributes.HTTP_ROUTE, "/somehost/high/{name}/{id}"));
  }

  @Test
  void shouldApplyServerDurationAndSizeView_stableSemconv() {
    Attributes startAttributes =
        Attributes.builder()
            .put(HttpAttributes.HTTP_REQUEST_METHOD, "GET")
            .put(
                UrlAttributes.URL_FULL, "https://somehost/high/cardinality/12345?jsessionId=121454")
            .put(UrlAttributes.URL_SCHEME, "https")
            .put(UrlAttributes.URL_PATH, "/high/cardinality/12345")
            .put(UrlAttributes.URL_QUERY, "jsessionId=121454")
            .put(NetworkAttributes.SERVER_ADDRESS, "somehost")
            .put(NetworkAttributes.SERVER_PORT, 443)
            .put(NetworkAttributes.CLIENT_ADDRESS, "somehost2")
            .put(NetworkAttributes.CLIENT_PORT, 443)
            .build();

    Attributes endAttributes =
        Attributes.builder()
            .put(SemanticAttributes.HTTP_ROUTE, "/somehost/high/{name}/{id}")
            .put(HttpAttributes.HTTP_RESPONSE_STATUS_CODE, 500)
            .put(NetworkAttributes.NETWORK_TRANSPORT, "tcp")
            .put(NetworkAttributes.NETWORK_TYPE, "ipv4")
            .put(NetworkAttributes.NETWORK_PROTOCOL_NAME, "http")
            .put(NetworkAttributes.NETWORK_PROTOCOL_VERSION, "1.1")
            .put(NetworkAttributes.SERVER_SOCKET_ADDRESS, "4.3.2.1")
            .put(NetworkAttributes.SERVER_SOCKET_PORT, 9090)
            .put(NetworkAttributes.CLIENT_SOCKET_ADDRESS, "1.2.3.4")
            .put(NetworkAttributes.CLIENT_SOCKET_PORT, 8080)
            .build();

    assertThat(applyServerDurationAndSizeView(startAttributes, endAttributes))
        .containsOnly(
            entry(HttpAttributes.HTTP_REQUEST_METHOD, "GET"),
            entry(HttpAttributes.HTTP_RESPONSE_STATUS_CODE, 500L),
            entry(SemanticAttributes.HTTP_ROUTE, "/somehost/high/{name}/{id}"),
            entry(UrlAttributes.URL_SCHEME, "https"),
            entry(NetworkAttributes.NETWORK_PROTOCOL_NAME, "http"),
            entry(NetworkAttributes.NETWORK_PROTOCOL_VERSION, "1.1"));
  }

  @Test
  void shouldApplyActiveRequestsView() {
    Attributes attributes =
        Attributes.builder()
            .put(SemanticAttributes.HTTP_METHOD, "GET")
            .put(
                SemanticAttributes.HTTP_URL,
                "https://somehost/high/cardinality/12345?jsessionId=121454")
            .put(SemanticAttributes.HTTP_TARGET, "/high/cardinality/12345?jsessionId=121454")
            .put(SemanticAttributes.HTTP_SCHEME, "https")
            .put(SemanticAttributes.NET_TRANSPORT, IP_TCP)
            .put(SemanticAttributes.NET_PROTOCOL_NAME, "http")
            .put(SemanticAttributes.NET_PROTOCOL_VERSION, "1.1")
            .put(SemanticAttributes.NET_HOST_NAME, "somehost")
            .put(SemanticAttributes.NET_HOST_PORT, 443)
            .put(SemanticAttributes.NET_SOCK_FAMILY, "inet")
            .put(SemanticAttributes.NET_SOCK_PEER_ADDR, "1.2.3.4")
            .put(SemanticAttributes.NET_SOCK_PEER_PORT, 8080)
            .put(SemanticAttributes.NET_SOCK_HOST_ADDR, "4.3.2.1")
            .put(SemanticAttributes.NET_SOCK_HOST_PORT, 9090)
            .build();

    assertThat(applyActiveRequestsView(attributes))
        .containsOnly(
            entry(SemanticAttributes.HTTP_METHOD, "GET"),
            entry(SemanticAttributes.HTTP_SCHEME, "https"),
            entry(SemanticAttributes.NET_HOST_NAME, "somehost"),
            entry(SemanticAttributes.NET_HOST_PORT, 443L));
  }

  @Test
  void shouldApplyActiveRequestsView_stableSemconv() {
    Attributes attributes =
        Attributes.builder()
            .put(HttpAttributes.HTTP_REQUEST_METHOD, "GET")
            .put(
                UrlAttributes.URL_FULL, "https://somehost/high/cardinality/12345?jsessionId=121454")
            .put(UrlAttributes.URL_SCHEME, "https")
            .put(UrlAttributes.URL_PATH, "/high/cardinality/12345")
            .put(UrlAttributes.URL_QUERY, "jsessionId=121454")
            .put(NetworkAttributes.NETWORK_TRANSPORT, "tcp")
            .put(NetworkAttributes.NETWORK_TYPE, "ipv4")
            .put(NetworkAttributes.NETWORK_PROTOCOL_NAME, "http")
            .put(NetworkAttributes.NETWORK_PROTOCOL_VERSION, "1.1")
            .put(NetworkAttributes.SERVER_ADDRESS, "somehost")
            .put(NetworkAttributes.SERVER_PORT, 443)
            .put(NetworkAttributes.SERVER_SOCKET_ADDRESS, "4.3.2.1")
            .put(NetworkAttributes.SERVER_SOCKET_PORT, 9090)
            .put(NetworkAttributes.CLIENT_SOCKET_ADDRESS, "1.2.3.4")
            .put(NetworkAttributes.CLIENT_SOCKET_PORT, 8080)
            .build();

    assertThat(applyActiveRequestsView(attributes))
        .containsOnly(
            entry(HttpAttributes.HTTP_REQUEST_METHOD, "GET"),
            entry(UrlAttributes.URL_SCHEME, "https"));
  }
}
