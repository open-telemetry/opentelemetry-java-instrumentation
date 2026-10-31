/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.storm.v2_0;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import javax.annotation.Nullable;
import org.apache.storm.tuple.TupleImpl;

public final class StormSingletons {

  private static final Instrumenter<StormEmit, Void> producerInstrumenter;
  private static final Instrumenter<TupleImpl, Void> processInstrumenter;

  /**
   * Holds the currently running emit, so that the {@link TupleImpl} created inside the emit call
   * can be linked to the producer context. It is only ever set on the thread performing the emit
   * and cleared once the emit returns.
   */
  private static final ThreadLocal<StormEmit> pendingEmit = new ThreadLocal<>();

  static {
    OpenTelemetry openTelemetry = GlobalOpenTelemetry.get();
    producerInstrumenter = StormInstrumenterFactory.createProducerInstrumenter(openTelemetry);
    processInstrumenter = StormInstrumenterFactory.createProcessInstrumenter(openTelemetry);
  }

  public static Instrumenter<StormEmit, Void> producerInstrumenter() {
    return producerInstrumenter;
  }

  public static Instrumenter<TupleImpl, Void> processInstrumenter() {
    return processInstrumenter;
  }

  public static void setPendingEmit(StormEmit emit) {
    pendingEmit.set(emit);
  }

  public static void clearPendingEmit() {
    pendingEmit.remove();
  }

  /** Copies the propagated headers of the running emit onto a freshly created tuple. */
  public static void attachHeadersToTuple(TupleImpl tuple) {
    StormEmit emit = pendingEmit.get();
    if (emit == null || emit.getHeaders().isEmpty()) {
      return;
    }
    VirtualFieldStore.setHeaders(tuple, emit.getHeaders());
  }

  /**
   * Returns {@code true} for Storm's internal streams (acks, ticks, metrics, ...). Those tuples are
   * an implementation detail of the runtime and are not part of the user's data flow.
   */
  public static boolean isInternalStream(@Nullable String streamId) {
    return streamId != null && streamId.startsWith("__");
  }

  private StormSingletons() {}
}
