/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.elasticsearch.rest.common.v5_0.internal;

import com.google.auto.value.AutoValue;
import javax.annotation.Nullable;
import org.apache.http.HttpEntity;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
@AutoValue
public abstract class ElasticsearchRestRequest {

  public static ElasticsearchRestRequest create(String method, String endpoint) {
    return create(method, endpoint, null, null);
  }

  public static ElasticsearchRestRequest create(
      String method,
      String endpoint,
      @Nullable ElasticsearchEndpointDefinition endpointDefinition,
      @Nullable HttpEntity httpEntity) {
    return create(method, endpoint, endpointDefinition, httpEntity, null);
  }

  public static ElasticsearchRestRequest create(
      String method,
      String endpoint,
      @Nullable ElasticsearchEndpointDefinition endpointDefinition,
      @Nullable HttpEntity httpEntity,
      @Nullable ElasticsearchServerTarget serverTarget) {
    return new AutoValue_ElasticsearchRestRequest(
        method, endpoint, endpointDefinition, httpEntity, serverTarget);
  }

  public abstract String getMethod();

  public abstract String getEndpoint();

  @Nullable
  public abstract ElasticsearchEndpointDefinition getEndpointDefinition();

  @Nullable
  public abstract HttpEntity getHttpEntity();

  @Nullable
  public abstract ElasticsearchServerTarget getServerTarget();
}
