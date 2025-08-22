/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.rpc;

import static io.opentelemetry.context.ContextKey.named;

import javax.annotation.Nullable;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.ImplicitContextKeyed;

public class RpcMetricsHolder implements ImplicitContextKeyed {

  private static final ContextKey<RpcMetricsHolder> KEY = named("opentelemetry-rpc-metrics");

  @Nullable
  private Long requestBodySize = null;
  @Nullable
  private Long responseBodySize = null;

  private RpcMetricsHolder() {}

  public static Context init(Context context) {
    if (context.get(KEY) != null) {
      return context;
    }
    return context.with(new RpcMetricsHolder());
  }

  public static void setRequestBodySize(Context context, long requestBodySize) {
    RpcMetricsHolder holder = context.get(KEY);
    if (holder != null) {
      holder.requestBodySize = requestBodySize;
    }
  }

  public static void setResponseBodySize(Context context, long responseBodySize) {
    RpcMetricsHolder holder = context.get(KEY);
    if (holder != null) {
      holder.responseBodySize = responseBodySize;
    }
  }

  public static Long getRequestBodySize(Context context) {
    RpcMetricsHolder holder = context.get(KEY);
    if (holder != null) {
      return holder.requestBodySize;
    }
    return null;
  }

  public static Long getResponseBodySize(Context context) {
    RpcMetricsHolder holder = context.get(KEY);
    if (holder != null) {
      return holder.responseBodySize;
    }
    return null;
  }

  @Override
  public Context storeInContext(Context context) {
    return context.with(KEY, this);
  }
}
