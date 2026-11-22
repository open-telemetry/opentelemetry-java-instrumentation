/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opensearch.v3_0;

import com.google.auto.value.AutoValue;
import io.opentelemetry.instrumentation.api.incubator.semconv.net.internal.ServerTarget;
import javax.annotation.Nullable;

@AutoValue
public abstract class OpenSearchRequest {

  public static OpenSearchRequest create(
      String method, @Nullable String body, @Nullable ServerTarget serverTarget) {
    return new AutoValue_OpenSearchRequest(method, body, serverTarget);
  }

  public abstract String getMethod();

  @Nullable
  public abstract String getBody();

  @Nullable
  public abstract ServerTarget getServerTarget();
}
