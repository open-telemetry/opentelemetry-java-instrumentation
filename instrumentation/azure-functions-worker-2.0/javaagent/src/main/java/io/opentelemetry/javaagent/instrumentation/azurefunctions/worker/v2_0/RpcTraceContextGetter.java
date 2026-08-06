/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.azurefunctions.worker.v2_0;

import static java.util.Arrays.asList;

import com.microsoft.azure.functions.rpc.messages.RpcTraceContext;
import io.opentelemetry.context.propagation.TextMapGetter;
import java.util.List;
import javax.annotation.Nullable;

enum RpcTraceContextGetter implements TextMapGetter<RpcTraceContext> {
  INSTANCE;

  // the host only ever sends w3c trace context
  private static final List<String> KEYS = asList("traceparent", "tracestate");

  @Override
  public Iterable<String> keys(RpcTraceContext carrier) {
    return KEYS;
  }

  @Override
  @Nullable
  public String get(@Nullable RpcTraceContext carrier, String key) {
    if (carrier == null) {
      return null;
    }
    switch (key) {
      case "traceparent":
        return carrier.getTraceParent();
      case "tracestate":
        return carrier.getTraceState();
      default:
        return null;
    }
  }
}
