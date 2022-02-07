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
  public void shouldApplyClientDurationView_urlWithQuery() {
    Attributes startAttributes =
        Attributes.builder()
            .put(
                SemanticAttributes.HTTP_URL,
                "https://somehost/high/cardinality/12345?jsessionId=121454")
            .put(SemanticAttributes.HTTP_METHOD, "GET")
            .put(SemanticAttributes.HTTP_SCHEME, "https")
            .put(SemanticAttributes.HTTP_HOST, "somehost")
            .put(SemanticAttributes.HTTP_TARGET, "/high/cardinality/12345?jsessionId=121454")
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
  public void shouldApplyServerDurationView_withRoute() {
    Attributes startAttributes =
        Attributes.builder()
            .put(SemanticAttributes.HTTP_METHOD, "GET")
            .put(
                SemanticAttributes.HTTP_URL,
                "https://somehost/high/cardinality/12345?jsessionId=121454")
            .put(SemanticAttributes.HTTP_SCHEME, "https")
            .put(SemanticAttributes.HTTP_HOST, "somehost")
            .put(SemanticAttributes.HTTP_SERVER_NAME, "somehost")
            .put(
                SemanticAttributes.HTTP_TARGET,
                "/somehost/high/cardinality/12345?jsessionId=121454")
            .put(SemanticAttributes.HTTP_ROUTE, "/somehost/high/{name}/{id}")
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
            attributeEntry(SemanticAttributes.HTTP_ROUTE.getKey(), "/somehost/high/{name}/{id}"),
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
