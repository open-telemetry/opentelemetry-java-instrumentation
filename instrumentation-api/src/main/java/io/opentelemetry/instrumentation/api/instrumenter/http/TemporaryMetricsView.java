/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.http;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BiConsumer;

// this is temporary, see
// https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/3962#issuecomment-906606325
@SuppressWarnings("rawtypes")
final class TemporaryMetricsView {

  private static final Set<AttributeKey> durationView = buildDurationView();

  private static final Set<AttributeKey> activeRequestsView = buildActiveRequestsView();

  private static Set<AttributeKey> buildDurationView() {
    // the list of included metrics is from
    // https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/metrics/semantic_conventions/http-metrics.md#attributes
    Set<AttributeKey> view = new HashSet<>();
    view.add(SemanticAttributes.HTTP_METHOD);
    view.add(SemanticAttributes.HTTP_HOST);
    view.add(SemanticAttributes.HTTP_SCHEME);
    view.add(SemanticAttributes.HTTP_STATUS_CODE);
    view.add(SemanticAttributes.HTTP_FLAVOR);
    view.add(SemanticAttributes.NET_PEER_NAME);
    view.add(SemanticAttributes.NET_PEER_PORT);
    view.add(SemanticAttributes.NET_PEER_IP);
    view.add(SemanticAttributes.HTTP_SERVER_NAME);
    view.add(SemanticAttributes.NET_HOST_NAME);
    view.add(SemanticAttributes.NET_HOST_PORT);
    return view;
  }

  private static Set<AttributeKey> buildActiveRequestsView() {
    // the list of included metrics is from
    // https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/metrics/semantic_conventions/http-metrics.md#attributes
    Set<AttributeKey> view = new HashSet<>();
    view.add(SemanticAttributes.HTTP_METHOD);
    view.add(SemanticAttributes.HTTP_HOST);
    view.add(SemanticAttributes.HTTP_SCHEME);
    view.add(SemanticAttributes.HTTP_FLAVOR);
    view.add(SemanticAttributes.HTTP_SERVER_NAME);
    return view;
  }

  static Attributes applyDurationView(Attributes startAttributes, Attributes endAttributes) {
    AttributesBuilder filtered = Attributes.builder();
    applyView(filtered, startAttributes, durationView);
    applyView(filtered, endAttributes, durationView);
    return filtered.build();
  }

  static Attributes applyActiveRequestsView(Attributes attributes) {
    AttributesBuilder filtered = Attributes.builder();
    applyView(filtered, attributes, activeRequestsView);
    return filtered.build();
  }

  @SuppressWarnings("unchecked")
  private static void applyView(
      AttributesBuilder filtered, Attributes attributes, Set<AttributeKey> view) {
    attributes.forEach(
        (BiConsumer<AttributeKey, Object>)
            (key, value) -> {
              if (view.contains(key)) {
                filtered.put(key, value);
              }
            });
  }

  private TemporaryMetricsView() {}
}
