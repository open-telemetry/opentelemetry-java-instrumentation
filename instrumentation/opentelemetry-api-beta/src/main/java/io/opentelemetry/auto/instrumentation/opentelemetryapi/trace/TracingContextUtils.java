/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.auto.instrumentation.opentelemetryapi.trace;

import static io.opentelemetry.auto.instrumentation.opentelemetryapi.trace.Bridging.toShadedOrNull;
import static io.opentelemetry.auto.instrumentation.opentelemetryapi.trace.Bridging.toUnshaded;

import io.opentelemetry.auto.bootstrap.ContextStore;
import io.opentelemetry.auto.instrumentation.opentelemetryapi.context.NoopScope;
import io.opentelemetry.auto.instrumentation.opentelemetryapi.context.UnshadedScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import unshaded.io.grpc.Context;
import unshaded.io.opentelemetry.context.Scope;
import unshaded.io.opentelemetry.trace.DefaultSpan;
import unshaded.io.opentelemetry.trace.Span;

public class TracingContextUtils {

  private static final Logger log = LoggerFactory.getLogger(TracingContextUtils.class);

  public static Context withSpan(
      final Span span,
      final Context context,
      final ContextStore<Context, io.grpc.Context> contextStore) {
    final io.opentelemetry.trace.Span shadedSpan = toShadedOrNull(span);
    if (shadedSpan == null) {
      if (log.isDebugEnabled()) {
        log.debug("unexpected span: {}", span, new Exception("unexpected span"));
      }
      return context;
    }
    final io.grpc.Context shadedContext = contextStore.get(context);
    if (shadedContext == null) {
      if (log.isDebugEnabled()) {
        log.debug("unexpected context: {}", context, new Exception("unexpected context"));
      }
      return context;
    }
    final io.grpc.Context updatedShadedContext =
        io.opentelemetry.trace.TracingContextUtils.withSpan(shadedSpan, shadedContext);
    final Context updatedContext = context.fork();
    contextStore.put(updatedContext, updatedShadedContext);
    return updatedContext;
  }

  public static Span getCurrentSpan() {
    return toUnshaded(io.opentelemetry.trace.TracingContextUtils.getCurrentSpan());
  }

  public static Span getSpan(
      final Context context, final ContextStore<Context, io.grpc.Context> contextStore) {
    final io.grpc.Context shadedContext = contextStore.get(context);
    if (shadedContext == null) {
      if (log.isDebugEnabled()) {
        log.debug("unexpected context: {}", context, new Exception("unexpected context"));
      }
      return DefaultSpan.getInvalid();
    }
    return toUnshaded(io.opentelemetry.trace.TracingContextUtils.getSpan(shadedContext));
  }

  public static Span getSpanWithoutDefault(
      final Context context, final ContextStore<Context, io.grpc.Context> contextStore) {
    final io.grpc.Context shadedContext = contextStore.get(context);
    if (shadedContext == null) {
      if (log.isDebugEnabled()) {
        log.debug("unexpected context: {}", context, new Exception("unexpected context"));
      }
      return null;
    }
    final io.opentelemetry.trace.Span shadedSpan =
        io.opentelemetry.trace.TracingContextUtils.getSpanWithoutDefault(shadedContext);
    return shadedSpan == null ? null : toUnshaded(shadedSpan);
  }

  public static Scope currentContextWith(final Span span) {
    if (!span.getContext().isValid()) {
      // this supports direct usage of unshaded DefaultSpan.getInvalid()
      return new UnshadedScope(
          io.opentelemetry.trace.TracingContextUtils.currentContextWith(
              io.opentelemetry.trace.DefaultSpan.getInvalid()));
    }
    if (span instanceof UnshadedSpan) {
      return new UnshadedScope(
          io.opentelemetry.trace.TracingContextUtils.currentContextWith(
              ((UnshadedSpan) span).getShadedSpan()));
    }
    if (log.isDebugEnabled()) {
      log.debug("unexpected span: {}", span, new Exception("unexpected span"));
    }
    return NoopScope.getInstance();
  }
}
