/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.metrics.View;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.HashSet;
import java.util.Set;

final class HttpMetricsView {
  public static final View durationClientView = buildDurationClientView();
  public static final View durationServerView = buildDurationServerView();
  public static final View activeRequestsView = buildActiveRequestsView();

  private static Set<AttributeKey<?>> buildDurationAlwaysInclude() {
    // the list of included metrics is from
    // https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/metrics/semantic_conventions/http-metrics.md#attributes
    Set<AttributeKey<?>> view = new HashSet<>();
    view.add(SemanticAttributes.HTTP_METHOD);
    view.add(SemanticAttributes.HTTP_STATUS_CODE); // Optional
    view.add(SemanticAttributes.HTTP_FLAVOR); // Optional
    return view;
  }

  private static View buildDurationClientView() {
    // We pull identifying attributes according to:
    // https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/metrics/semantic_conventions/http-metrics.md#attribute-alternatives
    // We only pull net.peer.name and net.peer.port because http.url has too high cardinality
    Set<AttributeKey<?>> view = new HashSet<>(buildDurationAlwaysInclude());
    view.add(SemanticAttributes.NET_PEER_NAME);
    view.add(SemanticAttributes.NET_PEER_PORT);
    view.add(AttributeKey.stringKey("net.peer.sock.addr"));
    return createView(stringifyView(view));
  }

  private static View buildDurationServerView() {
    // We pull identifying attributes according to:
    // https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/metrics/semantic_conventions/http-metrics.md#attribute-alternatives
    // With the following caveat:
    // - we always rely on http.route + http.host in this repository.
    // - we prefer http.route (which is scrubbed) over http.target (which is not scrubbed).
    Set<AttributeKey<?>> view = new HashSet<>(buildDurationAlwaysInclude());
    view.add(SemanticAttributes.HTTP_SCHEME);
    view.add(SemanticAttributes.NET_HOST_NAME);
    view.add(SemanticAttributes.NET_HOST_PORT);
    view.add(SemanticAttributes.HTTP_ROUTE);
    return createView(stringifyView(view));
  }

  private static View buildActiveRequestsView() {
    // the list of included metrics is from
    // https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/metrics/semantic_conventions/http-metrics.md#attributes
    Set<AttributeKey<?>> view = new HashSet<>();
    view.add(SemanticAttributes.HTTP_METHOD);
    view.add(SemanticAttributes.HTTP_SCHEME);
    view.add(SemanticAttributes.HTTP_FLAVOR);
    view.add(SemanticAttributes.NET_HOST_NAME);
    // TODO: net host port?
    return createView(stringifyView(view));
  }

  private static Set<String> stringifyView(Set<AttributeKey<?>> attributeKeys) {
    Set<String> keys = new HashSet<>(attributeKeys.size());
    attributeKeys.forEach(attributeKey -> keys.add(attributeKey.getKey()));
    return keys;
  }

  private static View createView(Set<String> attributeKeys) {
    return View.builder().setAttributeFilter(attributeKeys::contains).build();
  }

  private HttpMetricsView() {}
}
