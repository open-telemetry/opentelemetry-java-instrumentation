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

  private static final Set<AttributeKey> stableDurationAlwaysInclude =
      buildStableDurationAlwaysInclude();
  private static final Set<AttributeKey> stableDurationClientView = buildStableDurationClientView();
  private static final Set<AttributeKey> stableDurationServerView = buildStableDurationServerView();

  private static final Set<AttributeKey> oldDurationAlwaysInclude = buildOldDurationAlwaysInclude();
  private static final Set<AttributeKey> oldDurationClientView = buildOldDurationClientView();
  private static final Set<AttributeKey> oldDurationServerView = buildOldDurationServerView();

  private static final Set<AttributeKey> activeRequestsView = buildActiveRequestsView();

  private static Set<AttributeKey> buildStableDurationAlwaysInclude() {
    // the list of included metrics is from
    // https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/metrics/semantic_conventions/http-metrics.md#attributes
    Set<AttributeKey> view = new HashSet<>();
    // stable semconv
    view.add(HttpAttributes.HTTP_REQUEST_METHOD);
    view.add(HttpAttributes.HTTP_RESPONSE_STATUS_CODE);
    view.add(NetworkAttributes.NETWORK_PROTOCOL_NAME);
    view.add(NetworkAttributes.NETWORK_PROTOCOL_VERSION);
    return view;
  }

  private static Set<AttributeKey> buildOldDurationAlwaysInclude() {
    // the list of included metrics is from
    // https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/metrics/semantic_conventions/http-metrics.md#attributes
    Set<AttributeKey> view = new HashSet<>();
    view.add(SemanticAttributes.HTTP_METHOD);
    view.add(SemanticAttributes.HTTP_STATUS_CODE); // Optional
    view.add(SemanticAttributes.NET_PROTOCOL_NAME); // Optional
    view.add(SemanticAttributes.NET_PROTOCOL_VERSION); // Optional
    return view;
  }

  private static Set<AttributeKey> buildStableDurationClientView() {
    // We pull identifying attributes according to:
    // https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/metrics/semantic_conventions/http-metrics.md#attribute-alternatives
    Set<AttributeKey> view = new HashSet<>(stableDurationAlwaysInclude);
    view.add(NetworkAttributes.SERVER_SOCKET_ADDRESS);
    view.add(NetworkAttributes.SERVER_ADDRESS);
    view.add(NetworkAttributes.SERVER_PORT);
    return view;
  }

  private static Set<AttributeKey> buildOldDurationClientView() {
    // We pull identifying attributes according to:
    // https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/metrics/semantic_conventions/http-metrics.md#attribute-alternatives
    Set<AttributeKey> view = new HashSet<>(oldDurationAlwaysInclude);
    view.add(SemanticAttributes.NET_PEER_NAME);
    view.add(SemanticAttributes.NET_PEER_PORT);
    view.add(SemanticAttributes.NET_SOCK_PEER_ADDR);
    return view;
  }

  private static Set<AttributeKey> buildStableDurationServerView() {
    // We pull identifying attributes according to:
    // https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/metrics/semantic_conventions/http-metrics.md#attribute-alternatives
    Set<AttributeKey> view = new HashSet<>(stableDurationAlwaysInclude);
    view.add(UrlAttributes.URL_SCHEME);
    view.add(SemanticAttributes.HTTP_ROUTE);
    return view;
  }

  private static Set<AttributeKey> buildOldDurationServerView() {
    // We pull identifying attributes according to:
    // https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/metrics/semantic_conventions/http-metrics.md#attribute-alternatives
    Set<AttributeKey> view = new HashSet<>(oldDurationAlwaysInclude);
    view.add(SemanticAttributes.HTTP_SCHEME);
    view.add(SemanticAttributes.NET_HOST_NAME);
    view.add(SemanticAttributes.NET_HOST_PORT);
    view.add(SemanticAttributes.HTTP_ROUTE);
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

  static Attributes applyStableClientDurationView(
      Attributes startAttributes, Attributes endAttributes) {
    AttributesBuilder filtered = Attributes.builder();
    applyView(filtered, startAttributes, stableDurationClientView);
    applyView(filtered, endAttributes, stableDurationClientView);
    return filtered.build();
  }

  static Attributes applyOldClientDurationView(
      Attributes startAttributes, Attributes endAttributes) {
    AttributesBuilder filtered = Attributes.builder();
    applyView(filtered, startAttributes, oldDurationClientView);
    applyView(filtered, endAttributes, oldDurationClientView);
    return filtered.build();
  }

  static Attributes applyStableServerDurationView(
      Attributes startAttributes, Attributes endAttributes) {
    AttributesBuilder filtered = Attributes.builder();
    applyView(filtered, startAttributes, stableDurationServerView);
    applyView(filtered, endAttributes, stableDurationServerView);
    return filtered.build();
  }

  static Attributes applyOldServerDurationView(
      Attributes startAttributes, Attributes endAttributes) {
    AttributesBuilder filtered = Attributes.builder();
    applyView(filtered, startAttributes, oldDurationServerView);
    applyView(filtered, endAttributes, oldDurationServerView);
    return filtered.build();
  }

  static Attributes applyServerRequestSizeView(
      Attributes startAttributes, Attributes endAttributes) {
    AttributesBuilder filtered = Attributes.builder();
    applyView(filtered, startAttributes, stableDurationServerView);
    applyView(filtered, startAttributes, oldDurationServerView);
    applyView(filtered, endAttributes, stableDurationServerView);
    applyView(filtered, endAttributes, oldDurationServerView);
    return filtered.build();
  }

  static Attributes applyClientRequestSizeView(
      Attributes startAttributes, Attributes endAttributes) {
    AttributesBuilder filtered = Attributes.builder();
    applyView(filtered, startAttributes, stableDurationClientView);
    applyView(filtered, startAttributes, oldDurationClientView);
    applyView(filtered, endAttributes, stableDurationClientView);
    applyView(filtered, endAttributes, oldDurationClientView);
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
