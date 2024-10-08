/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2.internal;

import static io.opentelemetry.context.ContextKey.named;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.ImplicitContextKeyed;
import javax.annotation.Nullable;

final class SqsTracingContext implements ImplicitContextKeyed {

  private static final ContextKey<SqsTracingContext> KEY = named("sqs-tracing-context");

  private TracingList tracingList;

  public static void set(Context context, TracingList tracingList) {
    SqsTracingContext holder = context.get(KEY);
    if (holder != null) {
      holder.tracingList = tracingList;
    }
  }

  @Nullable
  public TracingList get() {
    return tracingList;
  }

  @Override
  public Context storeInContext(Context context) {
    return context.with(KEY, this);
  }
}
