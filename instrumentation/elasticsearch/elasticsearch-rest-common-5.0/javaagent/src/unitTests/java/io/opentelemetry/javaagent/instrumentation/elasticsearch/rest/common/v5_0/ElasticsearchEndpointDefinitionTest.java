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
  void routesAreImmutableAndKeepFirstMatchingRouteAndParameterOrder() {
    String[] routes = {"/{first_part}/{second}", "/{other}/{last}"};
    ElasticsearchEndpointDefinition endpoint =
        new ElasticsearchEndpointDefinition("test", routes, false);
    routes[0] = "/changed";

    assertThat(endpoint.getRoutes())
        .extracting(ElasticsearchEndpointDefinition.Route::getName)
        .containsExactly("/{first_part}/{second}", "/{other}/{last}");
    assertThatThrownBy(() -> endpoint.getRoutes().clear())
        .isInstanceOf(UnsupportedOperationException.class);

    List<String> parts = new ArrayList<>();
    endpoint.processPathParts("/one/two", (key, value) -> parts.add(key + "=" + value));
    assertThat(parts).containsExactly("first_part=one", "second=two");
  }
}
