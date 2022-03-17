/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.rest;

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class ElasticsearchRestRequest {

  public static ElasticsearchRestRequest create(String method, String endpoint) {
    return new AutoValue_ElasticsearchRestRequest(method, endpoint);
  }

  public abstract String getMethod();

  public abstract String getOperation();
}
