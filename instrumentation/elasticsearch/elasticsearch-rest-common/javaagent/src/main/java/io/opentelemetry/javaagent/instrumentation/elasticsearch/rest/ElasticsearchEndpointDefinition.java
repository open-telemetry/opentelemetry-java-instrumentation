/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.rest;

import javax.annotation.Nullable;

public class ElasticsearchEndpointDefinition {
  private final String endpointName;
  private final String[] routes;

  public ElasticsearchEndpointDefinition(String endpointName, String[] routes) {
    this.endpointName = endpointName;
    this.routes = routes;
  }

  @Nullable
  public String getEndpointName() {
    return endpointName;
  }

  @Nullable
  public String[] getRoutes() {
    return routes;
  }
}
