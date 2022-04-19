/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.internal.SpanKey;
import java.util.Map;
import java.util.Set;

interface SpanSuppressor {

  Context storeInContext(Context context, SpanKind spanKind, Span span);

  boolean shouldSuppress(Context parentContext, SpanKind spanKind);

  enum Noop implements SpanSuppressor {
    INSTANCE;

    @Override
    public Context storeInContext(Context context, SpanKind spanKind, Span span) {
      return context;
    }

    @Override
    public boolean shouldSuppress(Context parentContext, SpanKind spanKind) {
      return false;
    }
  }

  enum JustStoreServer implements SpanSuppressor {
    INSTANCE;

    @Override
    public Context storeInContext(Context context, SpanKind spanKind, Span span) {
      if (spanKind == SpanKind.SERVER) {
        return SpanKey.KIND_SERVER.storeInContext(context, span);
      }
      return context;
    }

    @Override
    public boolean shouldSuppress(Context parentContext, SpanKind spanKind) {
      return false;
    }
  }

  final class DelegateBySpanKind implements SpanSuppressor {

    private final Map<SpanKind, SpanSuppressor> delegates;

    DelegateBySpanKind(Map<SpanKind, SpanSuppressor> delegates) {
      this.delegates = delegates;
    }

    @Override
    public Context storeInContext(Context context, SpanKind spanKind, Span span) {
      SpanSuppressor delegate = delegates.get(spanKind);
      if (delegate == null) {
        return context;
      }
      return delegate.storeInContext(context, spanKind, span);
    }

    @Override
    public boolean shouldSuppress(Context parentContext, SpanKind spanKind) {
      SpanSuppressor delegate = delegates.get(spanKind);
      if (delegate == null) {
        return false;
      }
      return delegate.shouldSuppress(parentContext, spanKind);
    }
  }

  final class BySpanKey implements SpanSuppressor {

    private final Set<SpanKey> spanKeys;

    BySpanKey(Set<SpanKey> spanKeys) {
      this.spanKeys = spanKeys;
    }

    @Override
    public Context storeInContext(Context context, SpanKind spanKind, Span span) {
      for (SpanKey spanKey : spanKeys) {
        context = spanKey.storeInContext(context, span);
      }
      return context;
    }

    @Override
    public boolean shouldSuppress(Context parentContext, SpanKind spanKind) {
      for (SpanKey spanKey : spanKeys) {
        if (spanKey.fromContextOrNull(parentContext) == null) {
          return false;
        }
      }
      return true;
    }
  }
}
