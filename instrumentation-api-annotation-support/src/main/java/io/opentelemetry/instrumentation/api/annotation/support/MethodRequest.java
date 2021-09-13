/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.annotation.support;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ImplicitContextKeyed;
import java.lang.reflect.Method;
import org.checkerframework.checker.nullness.qual.Nullable;

public interface MethodRequest extends ImplicitContextKeyed {
  Method method();

  String name();

  SpanKind kind();

  @Nullable
  Object[] args();

  @Override
  default Context storeInContext(Context context) {
    return context.with(MethodRequestKey.KEY, this);
  }

  @Nullable
  static MethodRequest fromContextOrNull(Context context) {
    return context.get(MethodRequestKey.KEY);
  }
}
