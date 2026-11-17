/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.rest.common.v5_0;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ElasticsearchEndpointDefinitionTest {

  @Test
  void routesAreImmutableAndPathPartsRetainOrder() {
    String[] routes = {"/_search", "/{index_name}/_doc/{id}"};
    ElasticsearchEndpointDefinition endpoint =
        new ElasticsearchEndpointDefinition("test", routes, false);
    routes[1] = "/changed";

    assertThat(endpoint.getRoutes())
        .extracting(ElasticsearchEndpointDefinition.Route::getName)
        .containsExactly("/_search", "/{index_name}/_doc/{id}");
    assertThatThrownBy(() -> endpoint.getRoutes().clear())
        .isInstanceOf(UnsupportedOperationException.class);
    List<String> parts = new ArrayList<>();
    endpoint.processPathParts("/books/_doc/123", (key, value) -> parts.add(key + "=" + value));
    assertThat(parts).containsExactly("index_name=books", "id=123");
  }

  @Test
  void emptyRoutesHaveNoPathParts() {
    ElasticsearchEndpointDefinition endpoint =
        new ElasticsearchEndpointDefinition("test", new String[0], false);
    List<String> parts = new ArrayList<>();
    endpoint.processPathParts("/books", (key, value) -> parts.add(key + "=" + value));

    assertThat(endpoint.getRoutes()).isEmpty();
    assertThatThrownBy(() -> endpoint.getRoutes().clear())
        .isInstanceOf(UnsupportedOperationException.class);
    assertThat(parts).isEmpty();
  }
}
