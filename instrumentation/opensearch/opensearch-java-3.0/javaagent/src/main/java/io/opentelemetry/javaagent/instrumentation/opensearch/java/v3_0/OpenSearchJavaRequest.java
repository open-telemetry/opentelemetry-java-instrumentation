/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opensearch.java.v3_0;

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class OpenSearchJavaRequest {

  public static OpenSearchJavaRequest create(String method, String endpoint) {
    return new AutoValue_OpenSearchJavaRequest(method, endpoint);
  }

  public abstract String getMethod();

  public abstract String getOperation();
}
