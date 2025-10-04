/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opensearch.v3_0;

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class OpenSearchRequest {

  public static OpenSearchRequest create(String method, String endpoint) {
    return new AutoValue_OpenSearchRequest(method, endpoint);
  }

  public abstract String getMethod();

  public abstract String getOperation();
}
