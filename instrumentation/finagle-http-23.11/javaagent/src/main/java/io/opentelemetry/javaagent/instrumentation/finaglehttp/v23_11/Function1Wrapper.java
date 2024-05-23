/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.finaglehttp.v23_11;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import scala.Function1;

public final class Function1Wrapper {

  public static <T1, R> Function1<T1, R> wrap(Function1<T1, R> function1) {
    Context context = Context.current();
    return value -> {
      try (Scope ignored = context.makeCurrent()) {
        return function1.apply(value);
      }
    };
  }

  private Function1Wrapper() {}
}
