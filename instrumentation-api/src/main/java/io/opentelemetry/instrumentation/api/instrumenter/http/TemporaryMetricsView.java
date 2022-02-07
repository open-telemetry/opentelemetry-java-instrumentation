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
  private static final Set<AttributeKey> durationClientView = buildDurationClientView();
  private static final Set<AttributeKey> durationServerView = buildDurationServerView();
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

  private static Set<AttributeKey> buildDurationClientView() {
    // We pull identifying attributes according to:
    // https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/metrics/semantic_conventions/http-metrics.md#attribute-alternatives
    Set<AttributeKey> view = new HashSet<>(durationAlwaysInclude);
    view.add(SemanticAttributes.HTTP_URL);
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
    view.add(SemanticAttributes.HTTP_HOST);
    view.add(SemanticAttributes.HTTP_ROUTE);
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
    AttributesBuilder filtered = Attributes.builder();
    applyView(filtered, startAttributes, durationClientView);
    applyView(filtered, endAttributes, durationClientView);
    return filtered.build();
  }

  static Attributes applyServerDurationView(Attributes startAttributes, Attributes endAttributes) {
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
                // For now, we filter query parameters out of URLs in metrics.
                if (SemanticAttributes.HTTP_URL.equals(key)
                    || SemanticAttributes.HTTP_TARGET.equals(key)) {
                  filtered.put(key, removeQueryParamFromUrlOrTarget(value.toString()));
                } else {
                  filtered.put(key, value);
                }
              }
            });
  }

  // Attempt to handle cleaning URLs like http://myServer;jsessionId=1 or targets like
  // /my/path?queryParam=2
  private static String removeQueryParamFromUrlOrTarget(String urlOrTarget) {
    // Note: Maybe not the most robust, but purely to limit cardinality.
    int idx = -1;
    for (int i = 0; i < urlOrTarget.length(); ++i) {
      char ch = urlOrTarget.charAt(i);
      if (ch == '?' || ch == ';') {
        idx = i;
        break;
      }
    }
    return idx == -1 ? urlOrTarget : urlOrTarget.substring(0, idx);
  }

  private TemporaryMetricsView() {}
}
