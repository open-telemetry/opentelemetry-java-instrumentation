/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.rest;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.elasticsearch.rest.common.v5_0.internal.ElasticsearchEndpointDefinition;
import io.opentelemetry.javaagent.instrumentation.elasticsearch.apiclient.ElasticsearchEndpointMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ElasticsearchEndpointMapTest {

  private static final Set<String> SEARCH_ENDPOINTS =
      new HashSet<>(
          Arrays.asList(
              "search",
              "async_search.submit",
              "msearch",
              "eql.search",
              "terms_enum",
              "search_template",
              "msearch_template",
              "render_search_template"));

  private static List<String> getPathParts(String route) {
    List<String> pathParts = new ArrayList<>();
    String routeFragment = route;
    int paramStartIndex = routeFragment.indexOf('{');
    while (paramStartIndex >= 0) {
      int paramEndIndex = routeFragment.indexOf('}');
      if (paramEndIndex < 0 || paramEndIndex <= paramStartIndex + 1) {
        throw new IllegalStateException("Invalid route syntax!");
      }
      pathParts.add(routeFragment.substring(paramStartIndex + 1, paramEndIndex));

      int nextIdx = paramEndIndex + 1;
      if (nextIdx >= routeFragment.length()) {
        break;
      }

      routeFragment = routeFragment.substring(nextIdx);
      paramStartIndex = routeFragment.indexOf('{');
    }
    return pathParts;
  }

  @Test
  void testIsSearchEndpoint() {
    for (ElasticsearchEndpointDefinition esEndpointDefinition :
        ElasticsearchEndpointMap.getAllEndpoints()) {
      String endpointId = esEndpointDefinition.getEndpointName();
      assertThat(SEARCH_ENDPOINTS.contains(endpointId))
          .isEqualTo(esEndpointDefinition.isSearchEndpoint());
    }
  }

  @Test
  void testProcessPathParts() {
    for (ElasticsearchEndpointDefinition esEndpointDefinition :
        ElasticsearchEndpointMap.getAllEndpoints()) {
      for (String route :
          esEndpointDefinition.getRoutes().stream()
              .map(ElasticsearchEndpointDefinition.Route::getName)
              .collect(Collectors.toList())) {
        List<String> pathParts = getPathParts(route);
        String resolvedRoute = route.replace("{", "").replace("}", "");
        Map<String, String> observedParams = new HashMap<>();
        esEndpointDefinition.processPathParts(resolvedRoute, (k, v) -> observedParams.put(k, v));

        Map<String, String> expectedMap = new HashMap<>();
        pathParts.forEach(part -> expectedMap.put(part, part));

        assertThat(expectedMap).isEqualTo(observedParams);
      }
    }
  }

  @Test
  void testSearchEndpoint() {
    ElasticsearchEndpointDefinition esEndpoint = ElasticsearchEndpointMap.get("search");
    Map<String, String> observedParams = new HashMap<>();
    esEndpoint.processPathParts(
        "/test-index-1,test-index-2/_search", (k, v) -> observedParams.put(k, v));

    assertThat(observedParams.get("index")).isEqualTo("test-index-1,test-index-2");
  }

  @Test
  void testBuildRegexPattern() {
    Pattern pattern =
        ElasticsearchEndpointDefinition.EndpointPattern.buildRegexPattern(
            "/_nodes/{node_id}/shutdown");
    assertThat(pattern.pattern()).isEqualTo("^/_nodes/(?<node0id>[^/]+)/shutdown$");

    pattern =
        ElasticsearchEndpointDefinition.EndpointPattern.buildRegexPattern(
            "/_snapshot/{repository}/{snapshot}/_mount");
    assertThat(pattern.pattern())
        .isEqualTo("^/_snapshot/(?<repository>[^/]+)/(?<snapshot>[^/]+)/_mount$");

    pattern =
        ElasticsearchEndpointDefinition.EndpointPattern.buildRegexPattern(
            "/_security/profile/_suggest");
    assertThat(pattern.pattern()).isEqualTo("^/_security/profile/_suggest$");

    pattern =
        ElasticsearchEndpointDefinition.EndpointPattern.buildRegexPattern(
            "/_application/search_application/{name}");
    assertThat(pattern.pattern()).isEqualTo("^/_application/search_application/(?<name>[^/]+)$");

    pattern = ElasticsearchEndpointDefinition.EndpointPattern.buildRegexPattern("/");
    assertThat(pattern.pattern()).isEqualTo("^/$");
  }
}
