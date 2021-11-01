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

  private static final Set<AttributeKey> durationAlwaysInclude = buildDurationAlwaysInclude();

  private static final Set<AttributeKey> activeRequestsView = buildActiveRequestsView();


  private static Set<AttributeKey> buildDurationAlwaysInclude() {
    // the list of included metrics is from
    // https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/metrics/semantic_conventions/http-metrics.md#attributes
    Set<AttributeKey> view = new HashSet<>();
    view.add(SemanticAttributes.HTTP_METHOD);
    view.add(SemanticAttributes.HTTP_STATUS_CODE); // Optional
    view.add(SemanticAttributes.HTTP_FLAVOR); // Optional
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

  static Attributes applyClientDurationView(Attributes startAttributes, Attributes endAttributes) {
    Set<AttributeKey> fullSet = new HashSet<>(durationAlwaysInclude);
    // We pull identifying attributes according to:
    // https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/metrics/semantic_conventions/http-metrics.md#attribute-alternatives
    if (containsAttribute(SemanticAttributes.HTTP_URL, startAttributes, endAttributes)) {
      // Use http.url alone
      fullSet.add(SemanticAttributes.HTTP_URL);
    } else if (containsAttribute(SemanticAttributes.HTTP_HOST, startAttributes, endAttributes)) {
      // Use http.scheme, http.host and http.target
      fullSet.add(SemanticAttributes.HTTP_SCHEME);
      fullSet.add(SemanticAttributes.HTTP_HOST);
      fullSet.add(SemanticAttributes.HTTP_TARGET);
    } else if (containsAttribute(SemanticAttributes.NET_PEER_NAME, startAttributes, endAttributes)) {
      // Use http.scheme, net.peer.name, net.peer.port and http.target
      fullSet.add(SemanticAttributes.HTTP_SCHEME);
      fullSet.add(SemanticAttributes.NET_PEER_NAME);
      fullSet.add(SemanticAttributes.NET_PEER_PORT);
      fullSet.add(SemanticAttributes.HTTP_TARGET);
    } else {
      // Use http.scheme, net.peer.ip, net.peer.port and http.target
      fullSet.add(SemanticAttributes.HTTP_SCHEME);
      fullSet.add(SemanticAttributes.NET_PEER_IP);
      fullSet.add(SemanticAttributes.NET_PEER_PORT);
      fullSet.add(SemanticAttributes.HTTP_TARGET);
    }
    AttributesBuilder filtered = Attributes.builder();
    applyView(filtered, startAttributes, fullSet);
    applyView(filtered, endAttributes, fullSet);
    return filtered.build();
  }

  private static <T> boolean containsAttribute(AttributeKey<T> key, Attributes startAttributes, Attributes endAttributes) {
    return startAttributes.get(key) != null || endAttributes.get(key) != null;
  }

  static Attributes applyServerDurationView(Attributes startAttributes, Attributes endAttributes) {
    Set<AttributeKey> fullSet = new HashSet<>(durationAlwaysInclude);
    // We pull identifying attributes according to:
    // https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/metrics/semantic_conventions/http-metrics.md#attribute-alternatives
    if (containsAttribute(SemanticAttributes.HTTP_HOST, startAttributes, endAttributes)) {
      // Use http.scheme, http.host and http.target
      fullSet.add(SemanticAttributes.HTTP_SCHEME);
      fullSet.add(SemanticAttributes.HTTP_HOST);
      fullSet.add(SemanticAttributes.HTTP_TARGET);
    } else if (containsAttribute(SemanticAttributes.HTTP_SERVER_NAME, startAttributes, endAttributes)) {
      // Use http.scheme, http.server_name, net.host.port, http.target
      fullSet.add(SemanticAttributes.HTTP_SCHEME);
      fullSet.add(SemanticAttributes.HTTP_SERVER_NAME);
      fullSet.add(SemanticAttributes.HTTP_TARGET);
    } else if (containsAttribute(SemanticAttributes.NET_HOST_NAME, startAttributes, endAttributes)) {
      // Use http.scheme, net.host.name, net.host.port, http.target
      fullSet.add(SemanticAttributes.HTTP_SCHEME);
      fullSet.add(SemanticAttributes.NET_HOST_NAME);
      fullSet.add(SemanticAttributes.NET_HOST_PORT);
      fullSet.add(SemanticAttributes.HTTP_TARGET);
    } else {
      // Use http.url
      fullSet.add(SemanticAttributes.HTTP_URL);
    }
    AttributesBuilder filtered = Attributes.builder();
    applyView(filtered, startAttributes, fullSet);
    applyView(filtered, endAttributes, fullSet);
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
