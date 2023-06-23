/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.rest;

import com.google.auto.value.AutoValue;

import javax.annotation.Nullable;

@AutoValue
public abstract class ElasticsearchRestRequest {

  public static ElasticsearchRestRequest create(String method, String endpoint) {
    return create(method, endpoint, null);
  }

  public static ElasticsearchRestRequest create(String method, String endpoint,
      @Nullable ElasticsearchEndpointDefinition endpointDefinition) {
    return new AutoValue_ElasticsearchRestRequest(method, endpoint, endpointDefinition);
  }

  public abstract String getMethod();

  public abstract String getEndpoint();

  @Nullable
  public abstract ElasticsearchEndpointDefinition getEndpointDefinition();
}
