/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import java.time.Instant;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class InstrumenterUtil {

  private static InstrumenterAccess instrumenterAccess;
  private static InstrumenterBuilderAccess instrumenterBuilderAccess;

  public static void setInstrumenterAccess(InstrumenterAccess instrumenterAccess) {
    InstrumenterUtil.instrumenterAccess = instrumenterAccess;
  }

  public static void setInstrumenterBuilderAccess(
      InstrumenterBuilderAccess instrumenterBuilderAccess) {
    InstrumenterUtil.instrumenterBuilderAccess = instrumenterBuilderAccess;
  }

  public static <REQUEST, RESPONSE> Context startAndEnd(
      Instrumenter<REQUEST, RESPONSE> instrumenter,
      Context parentContext,
      REQUEST request,
      @Nullable RESPONSE response,
      @Nullable Throwable error,
      Instant startTime,
      Instant endTime) {
    // instrumenterAccess is guaranteed to be non-null here
    return instrumenterAccess.startAndEnd(
        instrumenter, parentContext, request, response, error, startTime, endTime);
  }

  public static <REQUEST, RESPONSE> Context suppressSpan(
      Instrumenter<REQUEST, RESPONSE> instrumenter, Context parentContext, REQUEST request) {
    return instrumenterAccess.suppressSpan(instrumenter, parentContext, request);
  }

  public static <REQUEST, RESPONSE> Instrumenter<REQUEST, RESPONSE> buildUpstreamInstrumenter(
      InstrumenterBuilder<REQUEST, RESPONSE> builder,
      TextMapGetter<REQUEST> getter,
      SpanKindExtractor<REQUEST> spanKindExtractor) {
    // instrumenterBuilderAccess is guaranteed to be non-null here
    return instrumenterBuilderAccess.buildUpstreamInstrumenter(builder, getter, spanKindExtractor);
  }

  public static <REQUEST, RESPONSE> Instrumenter<REQUEST, RESPONSE> buildDownstreamInstrumenter(
      InstrumenterBuilder<REQUEST, RESPONSE> builder,
      TextMapSetter<REQUEST> setter,
      SpanKindExtractor<REQUEST> spanKindExtractor) {
    // instrumenterBuilderAccess is guaranteed to be non-null here
    return instrumenterBuilderAccess.buildDownstreamInstrumenter(
        builder, setter, spanKindExtractor);
  }

  @CanIgnoreReturnValue
  public static <REQUEST, RESPONSE>
      InstrumenterBuilder<REQUEST, RESPONSE> propagateOperationListenersToOnEnd(
          InstrumenterBuilder<REQUEST, RESPONSE> builder) {
    // instrumenterBuilderAccess is guaranteed to be non-null here
    instrumenterBuilderAccess.propagateOperationListenersToOnEnd(builder);
    return builder;
  }

  private InstrumenterUtil() {}
}
