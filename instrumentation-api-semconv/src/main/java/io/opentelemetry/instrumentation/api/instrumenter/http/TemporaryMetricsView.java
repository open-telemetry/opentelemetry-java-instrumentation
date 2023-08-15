/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.http;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.http.internal.HttpAttributes;
import io.opentelemetry.instrumentation.api.instrumenter.network.internal.NetworkAttributes;
import io.opentelemetry.instrumentation.api.instrumenter.url.internal.UrlAttributes;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BiConsumer;

// this is temporary, see
// https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/3962#issuecomment-906606325
@SuppressWarnings("rawtypes")
final class TemporaryMetricsView {

  private static final Set<AttributeKey> durationAlwaysInclude = buildDurationAlwaysInclude();
  private static final Set<AttributeKey> durationClientView = buildDurationClientView();
  private static final Set<AttributeKey> durationServerView = buildDurationServerView();
  private static final Set<AttributeKey> activeRequestsView = buildActiveRequestsView();

  private static Set<AttributeKey> buildDurationAlwaysInclude() {
    // the list of included metrics is from
    // https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/metrics/semantic_conventions/http-metrics.md#attributes
    Set<AttributeKey> view = new HashSet<>();
    view.add(SemanticAttributes.HTTP_METHOD);
    view.add(SemanticAttributes.HTTP_STATUS_CODE); // Optional
    view.add(SemanticAttributes.NET_PROTOCOL_NAME); // Optional
    view.add(SemanticAttributes.NET_PROTOCOL_VERSION); // Optional
    // stable semconv
    view.add(HttpAttributes.HTTP_REQUEST_METHOD);
    view.add(HttpAttributes.HTTP_RESPONSE_STATUS_CODE);
    view.add(NetworkAttributes.NETWORK_PROTOCOL_NAME);
    view.add(NetworkAttributes.NETWORK_PROTOCOL_VERSION);
    return view;
  }

  private static Set<AttributeKey> buildDurationClientView() {
    // We pull identifying attributes according to:
    // https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/metrics/semantic_conventions/http-metrics.md#attribute-alternatives
    // We only pull net.peer.name and net.peer.port because http.url has too high cardinality
    Set<AttributeKey> view = new HashSet<>(durationAlwaysInclude);
    view.add(SemanticAttributes.NET_PEER_NAME);
    view.add(SemanticAttributes.NET_PEER_PORT);
    view.add(SemanticAttributes.NET_SOCK_PEER_ADDR);
    // stable semconv
    view.add(NetworkAttributes.SERVER_SOCKET_ADDRESS);
    view.add(NetworkAttributes.SERVER_ADDRESS);
    view.add(NetworkAttributes.SERVER_PORT);
    return view;
  }

  private static Set<AttributeKey> buildDurationServerView() {
    // We pull identifying attributes according to:
    // https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/metrics/semantic_conventions/http-metrics.md#attribute-alternatives
    // With the following caveat:
    // - we always rely on http.route + http.host in this repository.
    // - we prefer http.route (which is scrubbed) over http.target (which is not scrubbed).
    Set<AttributeKey> view = new HashSet<>(durationAlwaysInclude);
    view.add(SemanticAttributes.HTTP_SCHEME);
    view.add(SemanticAttributes.NET_HOST_NAME);
    view.add(SemanticAttributes.NET_HOST_PORT);
    view.add(SemanticAttributes.HTTP_ROUTE);
    // stable semconv
    view.add(UrlAttributes.URL_SCHEME);
    return view;
  }

  private static Set<AttributeKey> buildActiveRequestsView() {
    // the list of included metrics is from
    // https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/metrics/semantic_conventions/http-metrics.md#attributes
    Set<AttributeKey> view = new HashSet<>();
    view.add(SemanticAttributes.HTTP_METHOD);
    view.add(SemanticAttributes.HTTP_SCHEME);
    view.add(SemanticAttributes.NET_HOST_NAME);
    view.add(SemanticAttributes.NET_HOST_PORT);
    // stable semconv
    view.add(HttpAttributes.HTTP_REQUEST_METHOD);
    view.add(UrlAttributes.URL_SCHEME);
    return view;
  }

  static Attributes applyClientDurationAndSizeView(
      Attributes startAttributes, Attributes endAttributes) {
    AttributesBuilder filtered = Attributes.builder();
    applyView(filtered, startAttributes, durationClientView);
    applyView(filtered, endAttributes, durationClientView);
    return filtered.build();
  }

  static Attributes applyServerDurationAndSizeView(
      Attributes startAttributes, Attributes endAttributes) {
    AttributesBuilder filtered = Attributes.builder();
    applyView(filtered, startAttributes, durationServerView);
    applyView(filtered, endAttributes, durationServerView);
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
