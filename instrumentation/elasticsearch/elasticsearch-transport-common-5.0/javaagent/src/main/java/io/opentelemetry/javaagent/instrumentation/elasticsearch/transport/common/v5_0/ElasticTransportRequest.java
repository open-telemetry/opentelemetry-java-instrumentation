/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.common.v5_0;

import com.google.auto.value.AutoValue;
import io.opentelemetry.instrumentation.api.incubator.semconv.net.internal.ServerTarget;
import javax.annotation.Nullable;

@AutoValue
public abstract class ElasticTransportRequest {

  public static ElasticTransportRequest create(
      Object action, Object request, @Nullable ServerTarget serverTarget) {
    return new AutoValue_ElasticTransportRequest(action, request, serverTarget);
  }

  public abstract Object getAction();

  public abstract Object getRequest();

  @Nullable
  public abstract ServerTarget getServerTarget();
}
