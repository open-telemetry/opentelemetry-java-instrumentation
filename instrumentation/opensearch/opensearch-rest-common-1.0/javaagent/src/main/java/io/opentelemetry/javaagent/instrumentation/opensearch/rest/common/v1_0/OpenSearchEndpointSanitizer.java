/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opensearch.rest.common.v1_0;

import static java.util.Arrays.asList;

import java.util.HashSet;
import java.util.Set;

/**
 * Masks customer-controlled document identifiers in an OpenSearch REST endpoint path while leaving
 * the endpoint structure (index names and API keywords) intact, so {@code /my-index/_doc/12345}
 * becomes {@code /my-index/_doc/?}.
 *
 * <p>This is the single seam for endpoint sanitization: {@link
 * OpenSearchRestAttributesGetter#getDbQueryText} is the only caller. It masks exactly the path
 * parameters of the route that matches the request, using the same {@link OpenSearchEndpointMap}
 * route table that derives the operation name. Legacy typed document routes such as {@code
 * /{index}/{type}/{id}} are matched too, so their id is masked while the index and type stay as
 * structure.
 *
 * <p>When no route matches, masking falls back to the conservative keyword rule: only the segment
 * immediately following a known single-document identifier keyword is masked, so an unknown path
 * never has structural segments masked by mistake.
 */
final class OpenSearchEndpointSanitizer {

  private static final String MASKED_VALUE = "?";

  // Path segments that are immediately followed by a single-document identifier, e.g.
  // /{index}/_doc/{id} or /{index}/_update/{id}. The following segment is customer-controlled, so
  // it is masked. Used only as a fallback when no route matches.
  private static final Set<String> idIntroducingSegments =
      new HashSet<>(asList("_doc", "_create", "_update", "_source", "_explain", "_termvectors"));

  static String sanitize(String method, String endpoint) {
    int queryIdx = endpoint.indexOf('?');
    String path = queryIdx >= 0 ? endpoint.substring(0, queryIdx) : endpoint;
    String query = queryIdx >= 0 ? endpoint.substring(queryIdx) : "";

    String maskedPath = OpenSearchEndpointMap.maskPathParameters(method, path);
    if (maskedPath == null) {
      maskedPath = sanitizeByKeyword(path);
    }
    return maskedPath + maskQueryValues(query);
  }

  /**
   * Masks the value of every query parameter while keeping the parameter names, so {@code
   * ?routing=abc&refresh=true} becomes {@code ?routing=?&refresh=?}. A query parameter value is
   * customer-controlled just like a path id, so it must not appear in {@code db.query.text}.
   */
  private static String maskQueryValues(String query) {
    if (query.isEmpty()) {
      return query;
    }
    // query starts with '?'; keep it, then mask each name=value value
    String[] params = query.substring(1).split("&", -1);
    StringBuilder result = new StringBuilder("?");
    for (int i = 0; i < params.length; i++) {
      if (i > 0) {
        result.append('&');
      }
      String param = params[i];
      int eq = param.indexOf('=');
      if (eq >= 0) {
        result.append(param, 0, eq + 1).append(MASKED_VALUE);
      } else {
        result.append(param);
      }
    }
    return result.toString();
  }

  private static String sanitizeByKeyword(String endpoint) {
    String[] segments = endpoint.split("/", -1);
    String previous = null;
    for (int i = 0; i < segments.length; i++) {
      String segment = segments[i];
      if (segment.isEmpty()) {
        continue;
      }
      if (previous != null && idIntroducingSegments.contains(previous)) {
        segments[i] = MASKED_VALUE;
      }
      previous = segments[i];
    }
    return String.join("/", segments);
  }

  private OpenSearchEndpointSanitizer() {}
}
