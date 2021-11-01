/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.http;

import static io.opentelemetry.instrumentation.api.instrumenter.http.TemporaryMetricsView.applyActiveRequestsView;
import static io.opentelemetry.instrumentation.api.instrumenter.http.TemporaryMetricsView.applyClientDurationView;
import static io.opentelemetry.instrumentation.api.instrumenter.http.TemporaryMetricsView.applyServerDurationView;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.attributeEntry;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import org.junit.jupiter.api.Test;

public class TemporaryMetricsViewTest {

  @Test
  public void shouldApplyClientDurationView_urlIdentity() {
    Attributes startAttributes =
        Attributes.builder()
            .put(SemanticAttributes.HTTP_URL, "https://somehost/high/cardinality/12345")
            .put(SemanticAttributes.HTTP_SCHEME, "https")
            .put(SemanticAttributes.HTTP_METHOD, "GET")
            .put(SemanticAttributes.HTTP_HOST, "somehost")
            .put(SemanticAttributes.HTTP_TARGET, "/high/cardinality/12345")
            .build();

    Attributes endAttributes =
        Attributes.builder()
            .put(SemanticAttributes.HTTP_STATUS_CODE, 500)
            .put(SemanticAttributes.NET_PEER_NAME, "somehost2")
            .put(SemanticAttributes.NET_PEER_IP, "127.0.0.1")
            .put(SemanticAttributes.NET_PEER_PORT, 443)
            .build();

    OpenTelemetryAssertions.assertThat(applyClientDurationView(startAttributes, endAttributes))
        .containsOnly(
            attributeEntry(
                SemanticAttributes.HTTP_URL.getKey(), "https://somehost/high/cardinality/12345"),
            attributeEntry(SemanticAttributes.HTTP_METHOD.getKey(), "GET"),
            attributeEntry(SemanticAttributes.HTTP_STATUS_CODE.getKey(), 500));
  }

  @Test
  public void shouldApplyClientDurationView_httpHostIdentity() {
    Attributes startAttributes =
        Attributes.builder()
            .put(SemanticAttributes.HTTP_METHOD, "GET")
            .put(SemanticAttributes.HTTP_SCHEME, "https")
            .put(SemanticAttributes.HTTP_HOST, "somehost")
            .put(SemanticAttributes.HTTP_TARGET, "/high/cardinality/12345")
            .build();

    Attributes endAttributes =
        Attributes.builder()
            .put(SemanticAttributes.HTTP_STATUS_CODE, 500)
            .put(SemanticAttributes.NET_PEER_NAME, "somehost2")
            .put(SemanticAttributes.NET_PEER_IP, "127.0.0.1")
            .put(SemanticAttributes.NET_PEER_PORT, 443)
            .build();

    OpenTelemetryAssertions.assertThat(applyClientDurationView(startAttributes, endAttributes))
        .containsOnly(
            attributeEntry(SemanticAttributes.HTTP_SCHEME.getKey(), "https"),
            attributeEntry(SemanticAttributes.HTTP_HOST.getKey(), "somehost"),
            attributeEntry(SemanticAttributes.HTTP_TARGET.getKey(), "/high/cardinality/12345"),
            attributeEntry(SemanticAttributes.HTTP_METHOD.getKey(), "GET"),
            attributeEntry(SemanticAttributes.HTTP_STATUS_CODE.getKey(), 500));
  }

  @Test
  public void shouldApplyClientDurationView_netPeerNameIdentity() {
    Attributes startAttributes =
        Attributes.builder()
            .put(SemanticAttributes.HTTP_METHOD, "GET")
            .put(SemanticAttributes.HTTP_SCHEME, "https")
            .put(SemanticAttributes.HTTP_TARGET, "/high/cardinality/12345")
            .build();

    Attributes endAttributes =
        Attributes.builder()
            .put(SemanticAttributes.HTTP_STATUS_CODE, 500)
            .put(SemanticAttributes.NET_PEER_NAME, "somehost")
            .put(SemanticAttributes.NET_PEER_IP, "127.0.0.1")
            .put(SemanticAttributes.NET_PEER_PORT, 443)
            .build();

    OpenTelemetryAssertions.assertThat(applyClientDurationView(startAttributes, endAttributes))
        .containsOnly(
            attributeEntry(SemanticAttributes.HTTP_SCHEME.getKey(), "https"),
            attributeEntry(SemanticAttributes.NET_PEER_NAME.getKey(), "somehost"),
            attributeEntry(SemanticAttributes.NET_PEER_PORT.getKey(), 443),
            attributeEntry(SemanticAttributes.HTTP_TARGET.getKey(), "/high/cardinality/12345"),
            attributeEntry(SemanticAttributes.HTTP_METHOD.getKey(), "GET"),
            attributeEntry(SemanticAttributes.HTTP_STATUS_CODE.getKey(), 500));
  }

  @Test
  public void shouldApplyClientDurationView_netPeerIpIdentity() {
    Attributes startAttributes =
        Attributes.builder()
            .put(SemanticAttributes.HTTP_METHOD, "GET")
            .put(SemanticAttributes.HTTP_SCHEME, "https")
            .put(SemanticAttributes.HTTP_TARGET, "/high/cardinality/12345")
            .build();

    Attributes endAttributes =
        Attributes.builder()
            .put(SemanticAttributes.HTTP_STATUS_CODE, 500)
            .put(SemanticAttributes.NET_PEER_IP, "127.0.0.1")
            .put(SemanticAttributes.NET_PEER_PORT, 443)
            .build();

    OpenTelemetryAssertions.assertThat(applyClientDurationView(startAttributes, endAttributes))
        .containsOnly(
            attributeEntry(SemanticAttributes.HTTP_SCHEME.getKey(), "https"),
            attributeEntry(SemanticAttributes.NET_PEER_IP.getKey(), "127.0.0.1"),
            attributeEntry(SemanticAttributes.NET_PEER_PORT.getKey(), 443),
            attributeEntry(SemanticAttributes.HTTP_TARGET.getKey(), "/high/cardinality/12345"),
            attributeEntry(SemanticAttributes.HTTP_METHOD.getKey(), "GET"),
            attributeEntry(SemanticAttributes.HTTP_STATUS_CODE.getKey(), 500));
  }

  @Test
  public void shouldApplyServerDurationView_hostIdentity() {
    Attributes startAttributes =
        Attributes.builder()
            .put(SemanticAttributes.HTTP_METHOD, "GET")
            .put(SemanticAttributes.HTTP_URL, "https://somehost/high/cardinality/12345")
            .put(SemanticAttributes.HTTP_SCHEME, "https")
            .put(SemanticAttributes.HTTP_HOST, "somehost")
            .put(SemanticAttributes.HTTP_SERVER_NAME, "somehost")
            .put(SemanticAttributes.HTTP_TARGET, "/somehost/high/cardinality/12345")
            .put(SemanticAttributes.NET_HOST_NAME, "somehost")
            .put(SemanticAttributes.NET_HOST_PORT, 443)
            .build();

    Attributes endAttributes =
        Attributes.builder()
            .put(SemanticAttributes.HTTP_STATUS_CODE, 500)
            .put(SemanticAttributes.NET_PEER_NAME, "somehost2")
            .put(SemanticAttributes.NET_PEER_IP, "127.0.0.1")
            .put(SemanticAttributes.NET_PEER_PORT, 443)
            .build();

    OpenTelemetryAssertions.assertThat(applyServerDurationView(startAttributes, endAttributes))
        .containsOnly(
            attributeEntry(SemanticAttributes.HTTP_SCHEME.getKey(), "https"),
            attributeEntry(SemanticAttributes.HTTP_HOST.getKey(), "somehost"),
            attributeEntry(
                SemanticAttributes.HTTP_TARGET.getKey(), "/somehost/high/cardinality/12345"),
            attributeEntry(SemanticAttributes.HTTP_METHOD.getKey(), "GET"),
            attributeEntry(SemanticAttributes.HTTP_STATUS_CODE.getKey(), 500));
  }

  @Test
  public void shouldApplyServerDurationView_serverNameIdentity() {
    Attributes startAttributes =
        Attributes.builder()
            .put(SemanticAttributes.HTTP_METHOD, "GET")
            .put(SemanticAttributes.HTTP_URL, "https://somehost/high/cardinality/12345")
            .put(SemanticAttributes.HTTP_SCHEME, "https")
            .put(SemanticAttributes.HTTP_SERVER_NAME, "somehost")
            .put(SemanticAttributes.HTTP_TARGET, "/somehost/high/cardinality/12345")
            .put(SemanticAttributes.NET_HOST_NAME, "somehost")
            .put(SemanticAttributes.NET_HOST_PORT, 443)
            .build();

    Attributes endAttributes =
        Attributes.builder()
            .put(SemanticAttributes.HTTP_STATUS_CODE, 500)
            .put(SemanticAttributes.NET_PEER_NAME, "somehost2")
            .put(SemanticAttributes.NET_PEER_IP, "127.0.0.1")
            .put(SemanticAttributes.NET_PEER_PORT, 443)
            .build();

    OpenTelemetryAssertions.assertThat(applyServerDurationView(startAttributes, endAttributes))
        .containsOnly(
            attributeEntry(SemanticAttributes.HTTP_SCHEME.getKey(), "https"),
            attributeEntry(SemanticAttributes.HTTP_SERVER_NAME.getKey(), "somehost"),
            attributeEntry(
                SemanticAttributes.HTTP_TARGET.getKey(), "/somehost/high/cardinality/12345"),
            attributeEntry(SemanticAttributes.HTTP_METHOD.getKey(), "GET"),
            attributeEntry(SemanticAttributes.HTTP_STATUS_CODE.getKey(), 500));
  }

  @Test
  public void shouldApplyServerDurationView_netHostIdentity() {
    Attributes startAttributes =
        Attributes.builder()
            .put(SemanticAttributes.HTTP_METHOD, "GET")
            .put(SemanticAttributes.HTTP_URL, "https://somehost/high/cardinality/12345")
            .put(SemanticAttributes.HTTP_SCHEME, "https")
            .put(SemanticAttributes.HTTP_TARGET, "/somehost/high/cardinality/12345")
            .put(SemanticAttributes.NET_HOST_NAME, "somehost")
            .put(SemanticAttributes.NET_HOST_PORT, 443)
            .build();

    Attributes endAttributes =
        Attributes.builder()
            .put(SemanticAttributes.HTTP_STATUS_CODE, 500)
            .put(SemanticAttributes.NET_PEER_IP, "127.0.0.1")
            .put(SemanticAttributes.NET_PEER_PORT, 14204)
            .build();

    OpenTelemetryAssertions.assertThat(applyServerDurationView(startAttributes, endAttributes))
        .containsOnly(
            attributeEntry(SemanticAttributes.HTTP_SCHEME.getKey(), "https"),
            attributeEntry(SemanticAttributes.NET_HOST_NAME.getKey(), "somehost"),
            attributeEntry(SemanticAttributes.NET_HOST_PORT.getKey(), 443),
            attributeEntry(
                SemanticAttributes.HTTP_TARGET.getKey(), "/somehost/high/cardinality/12345"),
            attributeEntry(SemanticAttributes.HTTP_METHOD.getKey(), "GET"),
            attributeEntry(SemanticAttributes.HTTP_STATUS_CODE.getKey(), 500));
  }

  @Test
  public void shouldApplyServerDurationView_urlIdentity() {
    Attributes startAttributes =
        Attributes.builder()
            .put(SemanticAttributes.HTTP_METHOD, "GET")
            .put(SemanticAttributes.HTTP_URL, "https://somehost/high/cardinality/12345")
            .put(SemanticAttributes.HTTP_SCHEME, "https")
            .put(SemanticAttributes.HTTP_TARGET, "/somehost/high/cardinality/12345")
            .put(SemanticAttributes.NET_HOST_PORT, 443)
            .build();

    Attributes endAttributes =
        Attributes.builder()
            .put(SemanticAttributes.HTTP_STATUS_CODE, 500)
            .put(SemanticAttributes.NET_PEER_IP, "127.0.0.1")
            .put(SemanticAttributes.NET_PEER_PORT, 443)
            .build();

    OpenTelemetryAssertions.assertThat(applyServerDurationView(startAttributes, endAttributes))
        .containsOnly(
            attributeEntry(
                SemanticAttributes.HTTP_URL.getKey(), "https://somehost/high/cardinality/12345"),
            attributeEntry(SemanticAttributes.HTTP_METHOD.getKey(), "GET"),
            attributeEntry(SemanticAttributes.HTTP_STATUS_CODE.getKey(), 500));
  }

  @Test
  public void shouldApplyActiveRequestsView() {
    Attributes attributes =
        Attributes.builder()
            .put(SemanticAttributes.HTTP_METHOD, "GET")
            .put(SemanticAttributes.HTTP_URL, "/high/cardinality/12345")
            .put(SemanticAttributes.NET_PEER_NAME, "somehost")
            .build();

    OpenTelemetryAssertions.assertThat(applyActiveRequestsView(attributes))
        .containsOnly(attributeEntry("http.method", "GET"));
  }
}
