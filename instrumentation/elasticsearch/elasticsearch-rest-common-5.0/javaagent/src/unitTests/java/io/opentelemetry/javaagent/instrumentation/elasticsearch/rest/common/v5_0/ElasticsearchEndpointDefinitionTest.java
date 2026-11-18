/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.rest.common.v5_0;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ElasticsearchEndpointDefinitionTest {

  @Test
  void routesAreCopiedAndReadOnly() {
    String[] routeNames = {"/first/{id}", "/second/{name}"};
    ElasticsearchEndpointDefinition definition =
        new ElasticsearchEndpointDefinition("endpoint", routeNames, false);
    routeNames[0] = "/changed";

    assertThat(definition.getRoutes())
        .extracting(ElasticsearchEndpointDefinition.Route::getName)
        .containsExactly("/first/{id}", "/second/{name}");
    assertThatThrownBy(() -> definition.getRoutes().add(new ElasticsearchEndpointDefinition.Route("/other")))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void parameterNamesUseOwnedListsForParameterizedAndFixedRoutes() {
    ElasticsearchEndpointDefinition definition =
        new ElasticsearchEndpointDefinition("endpoint", new String[] {"/fixed", "/item/{item_id}"}, false);

    assertThat(definition.getRoutes().get(0).getPathPartNames()).isEmpty();
    assertThat(definition.getRoutes().get(1).getPathPartNames()).containsExactly("item0id");
    assertThatThrownBy(() -> definition.getRoutes().get(0).getPathPartNames().add("other"))
        .isInstanceOf(UnsupportedOperationException.class);

    List<Map.Entry<String, String>> captures = new ArrayList<>();
    definition.processPathParts("/item/42", (key, value) -> captures.add(new SimpleEntry<>(key, value)));
    assertThat(captures).containsExactly(new SimpleEntry<>("item_id", "42"));
  }
}
