/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opensearch.v3_0;

import com.google.auto.value.AutoValue;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbServerTarget;
import javax.annotation.Nullable;

@AutoValue
public abstract class OpenSearchRequest {

  public static OpenSearchRequest create(
      String method,
      String endpoint,
      @Nullable String body,
      @Nullable DbServerTarget serverTarget) {
    return new AutoValue_OpenSearchRequest(method, endpoint, body, serverTarget);
  }

  public abstract String getMethod();

  public abstract String getEndpoint();

  @Nullable
  public abstract String getBody();

  @Nullable
  public abstract DbServerTarget getServerTarget();
}
