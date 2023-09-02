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
import io.opentelemetry.semconv.SemanticAttributes;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BiConsumer;

// this is temporary, see
// https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/3962#issuecomment-906606325
@SuppressWarnings("rawtypes")
final class TemporaryMetricsView {

  private static final Set<AttributeKey> stableDurationClientView = buildStableDurationClientView();
  private static final Set<AttributeKey> stableDurationServerView = buildStableDurationServerView();

  private static final Set<AttributeKey> oldDurationClientView = buildOldDurationClientView();
  private static final Set<AttributeKey> oldDurationServerView = buildOldDurationServerView();

  private static final Set<AttributeKey> stableActiveRequestsView = buildStableActiveRequestsView();
  private static final Set<AttributeKey> oldActiveRequestsView = buildOldActiveRequestsView();

  private static Set<AttributeKey> buildStableDurationClientView() {
    // https://github.com/open-telemetry/semantic-conventions/blob/main/docs/http/http-metrics.md#metric-httpclientrequestduration
    Set<AttributeKey> view = new HashSet<>();
    view.add(HttpAttributes.HTTP_REQUEST_METHOD);
    view.add(HttpAttributes.HTTP_RESPONSE_STATUS_CODE);
    view.add(NetworkAttributes.NETWORK_PROTOCOL_NAME);
    view.add(NetworkAttributes.NETWORK_PROTOCOL_VERSION);
    view.add(NetworkAttributes.SERVER_ADDRESS);
    view.add(NetworkAttributes.SERVER_PORT);
    view.add(NetworkAttributes.SERVER_SOCKET_ADDRESS);
    return view;
  }

  @SuppressWarnings("deprecation") // until old http semconv are dropped in 2.0
  private static Set<AttributeKey> buildOldDurationClientView() {
    // https://github.com/open-telemetry/opentelemetry-specification/blob/v1.20.0/specification/metrics/semantic_conventions/http-metrics.md#metric-httpclientduration
    Set<AttributeKey> view = new HashSet<>();
    view.add(SemanticAttributes.HTTP_METHOD);
    view.add(SemanticAttributes.HTTP_STATUS_CODE);
    view.add(SemanticAttributes.NET_PEER_NAME);
    view.add(SemanticAttributes.NET_PEER_PORT);
    view.add(SemanticAttributes.NET_PROTOCOL_NAME);
    view.add(SemanticAttributes.NET_PROTOCOL_VERSION);
    view.add(SemanticAttributes.NET_SOCK_PEER_ADDR);
    return view;
  }

  private static Set<AttributeKey> buildStableDurationServerView() {
    // https://github.com/open-telemetry/semantic-conventions/blob/main/docs/http/http-metrics.md#metric-httpserverrequestduration
    Set<AttributeKey> view = new HashSet<>();
    view.add(SemanticAttributes.HTTP_ROUTE);
    view.add(HttpAttributes.HTTP_REQUEST_METHOD);
    view.add(HttpAttributes.HTTP_RESPONSE_STATUS_CODE);
    view.add(NetworkAttributes.NETWORK_PROTOCOL_NAME);
    view.add(NetworkAttributes.NETWORK_PROTOCOL_VERSION);
    view.add(UrlAttributes.URL_SCHEME);
    return view;
  }

  @SuppressWarnings("deprecation") // until old http semconv are dropped in 2.0
  private static Set<AttributeKey> buildOldDurationServerView() {
    // https://github.com/open-telemetry/opentelemetry-specification/blob/v1.20.0/specification/metrics/semantic_conventions/http-metrics.md#metric-httpserverduration
    Set<AttributeKey> view = new HashSet<>();
    view.add(SemanticAttributes.HTTP_SCHEME);
    view.add(SemanticAttributes.HTTP_ROUTE);
    view.add(SemanticAttributes.HTTP_METHOD);
    view.add(SemanticAttributes.HTTP_STATUS_CODE);
    view.add(SemanticAttributes.NET_HOST_NAME);
    view.add(SemanticAttributes.NET_HOST_PORT);
    view.add(SemanticAttributes.NET_PROTOCOL_NAME);
    view.add(SemanticAttributes.NET_PROTOCOL_VERSION);
    return view;
  }

  private static Set<AttributeKey> buildStableActiveRequestsView() {
    Set<AttributeKey> view = new HashSet<>();
    // https://github.com/open-telemetry/semantic-conventions/blob/main/docs/http/http-metrics.md#metric-httpserveractive_requests
    view.add(HttpAttributes.HTTP_REQUEST_METHOD);
    view.add(UrlAttributes.URL_SCHEME);
    return view;
  }

  @SuppressWarnings("deprecation") // until old http semconv are dropped in 2.0
  private static Set<AttributeKey> buildOldActiveRequestsView() {
    Set<AttributeKey> view = new HashSet<>();
    // https://github.com/open-telemetry/opentelemetry-specification/blob/v1.20.0/specification/metrics/semantic_conventions/http-metrics.md#metric-httpserveractive_requests
    view.add(SemanticAttributes.HTTP_METHOD);
    view.add(SemanticAttributes.HTTP_SCHEME);
    view.add(SemanticAttributes.NET_HOST_NAME);
    view.add(SemanticAttributes.NET_HOST_PORT);
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
    applyView(filtered, attributes, stableActiveRequestsView);
    applyView(filtered, attributes, oldActiveRequestsView);
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
