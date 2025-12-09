/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanStatusExtractor;
import java.time.Instant;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class InstrumenterUtil {

  private static InstrumenterAccess instrumenterAccess;
  private static InstrumenterBuilderAccess instrumenterBuilderAccess;

  @Initializer
  public static void setInstrumenterAccess(InstrumenterAccess instrumenterAccess) {
    InstrumenterUtil.instrumenterAccess = instrumenterAccess;
  }

  @Initializer
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

  public static <REQUEST, RESPONSE> Context suppressSpan(
      Instrumenter<REQUEST, RESPONSE> instrumenter, Context parentContext, SpanKind spanKind) {
    return instrumenterAccess.suppressSpan(instrumenter, parentContext, spanKind);
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

  public static <REQUESTFROM, RESPONSEFROM, REQUESTTO, RESPONSETO>
      UnaryOperator<SpanStatusExtractor<REQUESTTO, RESPONSETO>> convertSpanStatusExtractor(
          UnaryOperator<SpanStatusExtractor<REQUESTFROM, RESPONSEFROM>> extractor,
          Function<REQUESTFROM, REQUESTTO> requestConverter,
          Function<RESPONSEFROM, RESPONSETO> responseConverter,
          Function<REQUESTTO, REQUESTFROM> requestReverseConverter,
          Function<RESPONSETO, RESPONSEFROM> responseReverseConverter) {
    return inputExtractor -> {
      SpanStatusExtractor<REQUESTFROM, RESPONSEFROM> outputExtractor =
          extractor.apply(
              (spanStatusBuilder, requestFrom, responseFrom, error) ->
                  inputExtractor.extract(
                      spanStatusBuilder,
                      requestConverter.apply(requestFrom),
                      responseFrom == null ? null : responseConverter.apply(responseFrom),
                      error));
      return (spanStatusBuilder, requestTo, responseTo, error) ->
          outputExtractor.extract(
              spanStatusBuilder,
              requestReverseConverter.apply(requestTo),
              responseTo == null ? null : responseReverseConverter.apply(responseTo),
              error);
    };
  }

  public static <REQUESTFROM, REQUESTTO>
      UnaryOperator<SpanNameExtractor<REQUESTTO>> convertSpanNameExtractor(
          UnaryOperator<SpanNameExtractor<REQUESTFROM>> extractor,
          Function<REQUESTFROM, REQUESTTO> requestConverter,
          Function<REQUESTTO, REQUESTFROM> requestReverseConverter) {
    return inputExtractor -> {
      SpanNameExtractor<REQUESTFROM> outputExtractor =
          extractor.apply(
              requestFrom -> inputExtractor.extract(requestConverter.apply(requestFrom)));
      return requestTo -> outputExtractor.extract(requestReverseConverter.apply(requestTo));
    };
  }

  public static <REQUESTFROM, RESPONSEFROM, REQUESTTO, RESPONSETO>
      AttributesExtractor<REQUESTTO, RESPONSETO> convertAttributesExtractor(
          AttributesExtractor<REQUESTFROM, RESPONSEFROM> extractor,
          Function<REQUESTTO, REQUESTFROM> requestConverter,
          Function<RESPONSETO, RESPONSEFROM> responseConverter) {
    return new AttributesExtractor<REQUESTTO, RESPONSETO>() {
      @Override
      public void onStart(
          AttributesBuilder attributes, Context parentContext, REQUESTTO requestTo) {
        extractor.onStart(attributes, parentContext, requestConverter.apply(requestTo));
      }

      @Override
      public void onEnd(
          AttributesBuilder attributes,
          Context context,
          REQUESTTO requestTo,
          @Nullable RESPONSETO responseTo,
          @Nullable Throwable error) {
        extractor.onEnd(
            attributes,
            context,
            requestConverter.apply(requestTo),
            responseConverter.apply(responseTo),
            error);
      }
    };
  }

  private InstrumenterUtil() {}
}
