/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opensearch.rest.common.v1_0;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OpenSearchEndpointMapTest {

  // ---------------------------------------------------------------------------
  // Property tests over the whole table. These catch a malformed template, a
  // typo in a placeholder, or a structural-versus-parameter misclassification
  // anywhere in the registrations, including routes no spot check would list.
  // ---------------------------------------------------------------------------

  @Test
  void everyRouteResolvesToItsOwnOperationName() {
    int visited =
        forEachRoute(
            (method, route) -> {
              assertThat(route.getOperationName()).isNotEmpty();

              String concretePath = concretePath(route.getTemplate());
              assertThat(OpenSearchEndpointMap.getOperationName(method, concretePath))
                  .isEqualTo(route.getOperationName());
            });
    // guard against a vacuous pass: an emptied or gutted table must fail here
    assertThat(visited).isGreaterThanOrEqualTo(40);
  }

  @Test
  void everyRouteMasksParametersAndKeepsStructure() {
    int visited =
        forEachRoute(
            (method, route) -> {
              String concretePath = concretePath(route.getTemplate());
              String masked = OpenSearchEndpointMap.maskPathParameters(method, concretePath);
              assertThat(masked).isNotNull();

              List<String> segments = split(concretePath);
              List<String> maskedSegments = split(masked);
              assertThat(maskedSegments).hasSameSizeAs(segments);

              List<String> templateSegments = split(route.getTemplate());
              for (int i = 0; i < templateSegments.size(); i++) {
                String templateSegment = templateSegments.get(i);
                if (isParameter(templateSegment) && !isStructural(route, templateSegment)) {
                  assertThat(maskedSegments.get(i)).isEqualTo("?");
                } else {
                  assertThat(maskedSegments.get(i)).isEqualTo(segments.get(i));
                }
              }
            });
    // guard against a vacuous pass: an emptied or gutted table must fail here
    assertThat(visited).isGreaterThanOrEqualTo(40);
  }

  // ---------------------------------------------------------------------------
  // Targeted cases for behavior a property test cannot express.
  // ---------------------------------------------------------------------------

  @Test
  void unmappedRouteHasNoOperationName() {
    assertThat(OpenSearchEndpointMap.getOperationName("GET", "_totally/unknown/thing")).isNull();
    assertThat(OpenSearchEndpointMap.getOperationName("PATCH", "_search")).isNull();
  }

  @Test
  void getterFallsBackToHttpMethodWhenNoRouteMatches() {
    OpenSearchRestAttributesGetter getter = new OpenSearchRestAttributesGetter(true);
    OpenSearchRestRequest request = OpenSearchRestRequest.create("GET", "_totally/unknown/thing");
    assertThat(getter.getDbOperationName(request)).isEqualTo("GET");
  }

  @Test
  void samePathResolvesDifferentlyByMethod() {
    assertThat(OpenSearchEndpointMap.getOperationName("GET", "test-index/_doc/1")).isEqualTo("get");
    assertThat(OpenSearchEndpointMap.getOperationName("DELETE", "test-index/_doc/1"))
        .isEqualTo("delete");
    assertThat(OpenSearchEndpointMap.getOperationName("PUT", "test-index/_doc/1"))
        .isEqualTo("index");
    assertThat(OpenSearchEndpointMap.getOperationName("POST", "test-index/_doc/1"))
        .isEqualTo("index");
    // HEAD registers /{index}/_doc/{id} as exists, but not /{index}/_create/{id}
    assertThat(OpenSearchEndpointMap.getOperationName("HEAD", "test-index/_create/1")).isNull();
  }

  @Test
  void legacyTypedPutRouteDerivesIndexOperationAndMasksOnlyId() {
    assertThat(OpenSearchEndpointMap.getOperationName("PUT", "my-index/my-type/999"))
        .isEqualTo("index");
    assertThat(OpenSearchEndpointMap.maskPathParameters("PUT", "my-index/my-type/999"))
        .isEqualTo("my-index/my-type/?");
  }

  @Test
  void pinsScrollRouteOperationNames() {
    assertThat(OpenSearchEndpointMap.getOperationName("GET", "_search/scroll")).isEqualTo("scroll");
    assertThat(OpenSearchEndpointMap.getOperationName("POST", "_search/scroll"))
        .isEqualTo("scroll");
    assertThat(OpenSearchEndpointMap.getOperationName("GET", "_search/scroll/abc123"))
        .isEqualTo("scroll");
    assertThat(OpenSearchEndpointMap.getOperationName("POST", "_search/scroll/abc123"))
        .isEqualTo("scroll");
    assertThat(OpenSearchEndpointMap.getOperationName("DELETE", "_search/scroll/abc123"))
        .isEqualTo("clear_scroll");
  }

  @Test
  void nodeStatsRoutesMaskNodeIdAndKeepMetricSelectors() {
    assertThat(
            OpenSearchEndpointMap.getOperationName("GET", "_nodes/nodeA/stats/indices/fielddata"))
        .isEqualTo("nodes.stats");
    assertThat(
            OpenSearchEndpointMap.maskPathParameters("GET", "_nodes/nodeA/stats/indices/fielddata"))
        .isEqualTo("_nodes/?/stats/indices/fielddata");
    assertThat(OpenSearchEndpointMap.getOperationName("GET", "_nodes/stats/_all"))
        .isEqualTo("nodes.stats");
    assertThat(OpenSearchEndpointMap.maskPathParameters("GET", "_nodes/stats/_all"))
        .isEqualTo("_nodes/stats/_all");
  }

  @Test
  void masksIdBearingSegmentAndKeepsStructureIntact() {
    assertThat(OpenSearchEndpointMap.maskPathParameters("PUT", "test-index/_doc/12345"))
        .isEqualTo("test-index/_doc/?");
    assertThat(OpenSearchEndpointMap.maskPathParameters("GET", "_search/scroll/abc123"))
        .isEqualTo("_search/scroll/?");
    // a structural-only path carries no id to mask
    assertThat(OpenSearchEndpointMap.maskPathParameters("GET", "test-index/_search"))
        .isEqualTo("test-index/_search");
  }

  @Test
  void unmatchedPathHasNothingToMask() {
    assertThat(OpenSearchEndpointMap.maskPathParameters("GET", "_totally/unknown/thing")).isNull();
  }

  // ---------------------------------------------------------------------------
  // Sanitizer seam: query-string masking and keyword fallback.
  // ---------------------------------------------------------------------------

  @Test
  void masksQueryParameterValuesButKeepsNames() {
    // regression: the query string used to be re-appended verbatim, leaking routing=abc
    assertThat(OpenSearchEndpointSanitizer.sanitize("GET", "my-index/_doc/12345?routing=abc"))
        .isEqualTo("my-index/_doc/??routing=?");
    assertThat(
            OpenSearchEndpointSanitizer.sanitize(
                "GET", "my-index/_doc/12345?routing=abc&refresh=true"))
        .isEqualTo("my-index/_doc/??routing=?&refresh=?");
    // a query on a search path (never id-masked) still has its values masked
    assertThat(OpenSearchEndpointSanitizer.sanitize("GET", "my-index/_search?q=secret"))
        .isEqualTo("my-index/_search?q=?");
  }

  @Test
  void bothScrollSpellingsMaskTheScrollId() {
    // path form: the id is a path segment
    assertThat(OpenSearchEndpointSanitizer.sanitize("GET", "_search/scroll/DXF1ZXJ5QW5k"))
        .isEqualTo("_search/scroll/?");
    // query form (the canonical call): the id is a query parameter value
    assertThat(OpenSearchEndpointSanitizer.sanitize("GET", "_search/scroll?scroll_id=DXF1ZXJ5QW5k"))
        .isEqualTo("_search/scroll?scroll_id=?");
  }

  @Test
  void keywordFallbackMasksIdAndQueryValuesForUnmappedPath() {
    assertThat(OpenSearchEndpointSanitizer.sanitize("GET", "my-index/_doc/12345/_unknown?x=y"))
        .isEqualTo("my-index/_doc/?/_unknown?x=?");
  }

  @Test
  void underscorePrefixedPathIsNotSwallowedByTypedRoute() {
    // {index}/{type} compile to [^_/][^/]*, so a reserved _-prefixed first segment cannot match the
    // generic /{index}/{type}/{id} route (GET -> "get"). Pin it: _nodes/nodeA/stats resolves to
    // nodes.stats, not the legacy typed "get" route.
    assertThat(OpenSearchEndpointMap.getOperationName("GET", "_nodes/nodeA/stats"))
        .isEqualTo("nodes.stats");
    // and its masking keeps the structural keywords, masking only the {node_id}
    assertThat(OpenSearchEndpointMap.maskPathParameters("GET", "_nodes/nodeA/stats"))
        .isEqualTo("_nodes/?/stats");
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private interface RouteConsumer {
    void accept(String method, OpenSearchEndpointRoute route);
  }

  private static int forEachRoute(RouteConsumer consumer) {
    int visited = 0;
    for (Map.Entry<String, List<OpenSearchEndpointRoute>> entry :
        OpenSearchEndpointMap.getRoutesByMethod().entrySet()) {
      for (OpenSearchEndpointRoute route : entry.getValue()) {
        consumer.accept(entry.getKey(), route);
        visited++;
      }
    }
    return visited;
  }

  /** Turns a template such as {@code /{index}/_doc/{id}} into a concrete path. */
  private static String concretePath(String template) {
    StringBuilder result = new StringBuilder();
    for (String segment : split(template)) {
      result.append('/');
      if (isParameter(segment)) {
        // a value that starts with a letter, so it satisfies both the strict
        // {index}/{type} regex ([^_/][^/]*) and the general parameter regex
        result.append('x').append(segment, 1, segment.length() - 1);
      } else {
        result.append(segment);
      }
    }
    return result.toString();
  }

  private static boolean isParameter(String segment) {
    return segment.startsWith("{") && segment.endsWith("}");
  }

  private static boolean isStructural(OpenSearchEndpointRoute route, String templateSegment) {
    String name = templateSegment.substring(1, templateSegment.length() - 1);
    return route.isStructuralGroup(name.replace("_", "0"));
  }

  private static List<String> split(String path) {
    String trimmed = path.startsWith("/") ? path.substring(1) : path;
    return asList(trimmed.split("/", -1));
  }
}
