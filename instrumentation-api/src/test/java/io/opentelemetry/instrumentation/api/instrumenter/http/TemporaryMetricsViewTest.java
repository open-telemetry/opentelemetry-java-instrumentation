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
    Attributes startAttributes =
        Attributes.builder()
            .put(SemanticAttributes.HTTP_METHOD, "GET")
            .put(SemanticAttributes.HTTP_URL, "http://somehost/high/cardinality/12345")
            .put(SemanticAttributes.NET_PEER_NAME, "somehost")
            .build();

    Attributes endAttributes =
        Attributes.builder()
            .put(SemanticAttributes.HTTP_STATUS_CODE, 500)
            .put(SemanticAttributes.NET_PEER_NAME, "somehost2")
            .build();

    OpenTelemetryAssertions.assertThat(applyDurationView(startAttributes, endAttributes))
        .containsOnly(
            attributeEntry(SemanticAttributes.HTTP_METHOD.getKey(), "GET"),
            attributeEntry(SemanticAttributes.NET_PEER_NAME.getKey(), "somehost2"),
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
