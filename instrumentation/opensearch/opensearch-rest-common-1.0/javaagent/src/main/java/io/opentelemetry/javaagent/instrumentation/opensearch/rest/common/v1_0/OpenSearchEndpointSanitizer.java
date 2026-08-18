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
 * OpenSearchRestAttributesGetter#getDbQueryText} is the only caller. It intentionally works from
 * the raw path string alone, because {@link OpenSearchRestRequest} carries no endpoint metadata. A
 * route-based replacement (a parameterized route table that masks exactly the path parameters) can
 * swap this implementation without touching the getter.
 *
 * <p>Masking is conservative: only the segment immediately following a known single-document
 * identifier keyword is masked. Identifier-bearing routes that carry no such keyword, such as the
 * legacy typed {@code /{index}/{type}/{id}} document route, are left intact to avoid masking
 * segments that are really endpoint structure.
 */
final class OpenSearchEndpointSanitizer {

  private static final String MASKED_VALUE = "?";

  // Path segments that are immediately followed by a single-document identifier, e.g.
  // /{index}/_doc/{id} or /{index}/_update/{id}. The following segment is customer-controlled, so
  // it is masked.
  private static final Set<String> idIntroducingSegments =
      new HashSet<>(asList("_doc", "_create", "_update", "_source", "_explain", "_termvectors"));

  static String sanitize(String endpoint) {
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
