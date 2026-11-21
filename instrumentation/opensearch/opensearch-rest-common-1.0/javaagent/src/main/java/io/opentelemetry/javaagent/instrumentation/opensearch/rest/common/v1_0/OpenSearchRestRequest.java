/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opensearch.rest.common.v1_0;

import com.google.auto.value.AutoValue;
import io.opentelemetry.instrumentation.api.incubator.semconv.net.internal.ServerTarget;
import javax.annotation.Nullable;

@AutoValue
public abstract class OpenSearchRestRequest {

  public static OpenSearchRestRequest create(String method, @Nullable ServerTarget serverTarget) {
    return new AutoValue_OpenSearchRestRequest(method, serverTarget);
  }

  public abstract String getMethod();

  @Nullable
  public abstract ServerTarget getServerTarget();
}
