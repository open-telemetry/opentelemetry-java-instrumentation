/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.grpc.v1_6.internal;

import io.grpc.Context;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class GrpcServerStreamTracerState {

  // A JDK value type lets application and shaded agent tracers share the same per-call state.
  private static final Context.Key<AtomicBoolean> KEY =
      Context.key("otel-grpc-stream-tracer-state");

  public static Context.Key<AtomicBoolean> getKey() {
    return KEY;
  }

  private GrpcServerStreamTracerState() {}
}
