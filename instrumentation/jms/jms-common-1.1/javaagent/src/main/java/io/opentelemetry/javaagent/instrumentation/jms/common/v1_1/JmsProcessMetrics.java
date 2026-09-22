/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jms.common.v1_1;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingProcessMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.OperationListener;
import io.opentelemetry.instrumentation.api.instrumenter.OperationMetrics;

final class JmsProcessMetrics {

  private static final ContextKey<Context> DURATION_CONTEXT =
      ContextKey.named("jms-process-duration-context");

  static OperationMetrics create() {
    return meter -> create(MessagingProcessMetrics.get().create(meter));
  }

  static OperationListener create(OperationListener duration) {
    return new OperationListener() {
      @Override
      public Context onStart(Context context, Attributes startAttributes, long startNanos) {
        Context durationContext = Span.fromContext(context).storeInContext(Context.root());
        return context.with(
            DURATION_CONTEXT, duration.onStart(durationContext, startAttributes, startNanos));
      }

      @Override
      public void onEnd(Context context, Attributes endAttributes, long endNanos) {
        Context durationContext = context.get(DURATION_CONTEXT);
        if (durationContext == null) {
          return;
        }
        duration.onEnd(durationContext, endAttributes, endNanos);
      }
    };
  }

  private JmsProcessMetrics() {}
}
