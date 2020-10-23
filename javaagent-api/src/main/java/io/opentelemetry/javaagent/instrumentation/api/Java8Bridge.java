/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.api;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;

/**
 * A helper for accessing methods that rely on Java 8 bytecode such as static interface methods. In
 * instrumentation, we may need to call these methods in code that is inlined into an instrumented
 * class, however many times the instrumented class has been compiled to a previous version of
 * bytecode and we cannot inline Java 8 method calls.
 */
public final class Java8Bridge {

  /** Calls {@link Context#current()}. */
  public static Context currentContext() {
    return Context.current();
  }

  /** Calls {@link Span#current()}. */
  public static Span currentSpan() {
    return Span.current();
  }

  /** Calls {@link Span#fromContext(Context)}. */
  public static Span spanFromContext(Context context) {
    return Span.fromContext(context);
  }

  public static ContextPropagators getGlobalPropagators() {
    return OpenTelemetry.getGlobalPropagators();
  }

  /** Calls {@link OpenTelemetry#getGlobalTracer(String)} */
  public static Tracer getGlobalTracer(String name) {
    return OpenTelemetry.getGlobalTracer(name);
  }
}
