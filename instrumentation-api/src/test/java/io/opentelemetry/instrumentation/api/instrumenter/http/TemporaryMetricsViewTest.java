/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.http;

import static io.opentelemetry.instrumentation.api.instrumenter.http.TemporaryMetricsView.applyActiveRequestsView;
import static io.opentelemetry.instrumentation.api.instrumenter.http.TemporaryMetricsView.applyDurationView;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.attributeEntry;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import org.junit.jupiter.api.Test;

public class TemporaryMetricsViewTest {

  @Test
  public void shouldApplyDurationView() {
    Attributes attributes =
        Attributes.builder()
            .put(SemanticAttributes.HTTP_METHOD, "GET")
            .put(SemanticAttributes.HTTP_URL, "http://somehost/high/cardinality/12345")
            .put(SemanticAttributes.NET_PEER_NAME, "somehost")
            .build();

    OpenTelemetryAssertions.assertThat(applyDurationView(attributes))
        .containsOnly(
            attributeEntry("http.method", "GET"), attributeEntry("net.peer.name", "somehost"));
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
