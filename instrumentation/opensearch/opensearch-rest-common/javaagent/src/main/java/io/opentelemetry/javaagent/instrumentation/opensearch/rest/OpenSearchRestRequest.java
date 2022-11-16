/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opensearch.rest;

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class OpenSearchRestRequest {

  public static OpenSearchRestRequest create(String method, String endpoint) {
    return new AutoValue_OpenSearchRestRequest(method, endpoint);
  }

  public abstract String getMethod();

  public abstract String getOperation();
}
