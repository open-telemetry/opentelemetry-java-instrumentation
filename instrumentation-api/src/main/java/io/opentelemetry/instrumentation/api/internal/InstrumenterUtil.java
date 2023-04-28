/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class InstrumenterUtil {

  private static final Logger logger = Logger.getLogger(InstrumenterUtil.class.getName());

  private static final Method startAndEndMethod;
  private static final Method buildUpstreamInstrumenterMethod;
  private static final Method buildDownstreamInstrumenterMethod;

  static {
    Method startAndEnd = null;
    Method buildUpstreamInstrumenter = null;
    Method buildDownstreamInstrumenter = null;
    try {
      startAndEnd =
          Instrumenter.class.getDeclaredMethod(
              "startAndEnd",
              Context.class,
              Object.class,
              Object.class,
              Throwable.class,
              Instant.class,
              Instant.class);
      startAndEnd.setAccessible(true);
      buildUpstreamInstrumenter =
          InstrumenterBuilder.class.getDeclaredMethod(
              "buildUpstreamInstrumenter", TextMapGetter.class, SpanKindExtractor.class);
      buildUpstreamInstrumenter.setAccessible(true);
      buildDownstreamInstrumenter =
          InstrumenterBuilder.class.getDeclaredMethod(
              "buildDownstreamInstrumenter", TextMapSetter.class, SpanKindExtractor.class);
      buildDownstreamInstrumenter.setAccessible(true);
    } catch (NoSuchMethodException e) {
      logger.log(
          Level.WARNING,
          "Could not get Instrumenter and InstrumenterBuilder methods with reflection",
          e);
    }
    startAndEndMethod = startAndEnd;
    buildUpstreamInstrumenterMethod = buildUpstreamInstrumenter;
    buildDownstreamInstrumenterMethod = buildDownstreamInstrumenter;
  }

  public static <REQUEST, RESPONSE> Context startAndEnd(
      Instrumenter<REQUEST, RESPONSE> instrumenter,
      Context parentContext,
      REQUEST request,
      @Nullable RESPONSE response,
      @Nullable Throwable error,
      Instant startTime,
      Instant endTime) {

    if (startAndEndMethod == null) {
      logger.log(Level.WARNING, "Could not get Instrumenter#startAndEnd() method with reflection");
      return parentContext;
    }
    try {
      return (Context)
          startAndEndMethod.invoke(
              instrumenter, parentContext, request, response, error, startTime, endTime);
    } catch (InvocationTargetException | IllegalAccessException e) {
      logger.log(Level.WARNING, "Error occurred when calling Instrumenter#startAndEnd()", e);
      return parentContext;
    }
  }

  @SuppressWarnings("unchecked")
  public static <REQUEST, RESPONSE> Instrumenter<REQUEST, RESPONSE> buildUpstreamInstrumenter(
      InstrumenterBuilder<REQUEST, RESPONSE> builder,
      TextMapGetter<REQUEST> getter,
      SpanKindExtractor<REQUEST> spanKindExtractor) {
    if (buildUpstreamInstrumenterMethod == null) {
      logger.log(
          Level.WARNING,
          "Could not get InstrumenterBuilder#buildUpstreamInstrumenter() method with reflection");
      return builder.buildInstrumenter(spanKindExtractor);
    }
    try {
      return (Instrumenter<REQUEST, RESPONSE>)
          buildUpstreamInstrumenterMethod.invoke(builder, getter, spanKindExtractor);
    } catch (InvocationTargetException | IllegalAccessException e) {
      logger.log(
          Level.WARNING,
          "Error occurred when calling InstrumenterBuilder#buildUpstreamInstrumenter()",
          e);
      return builder.buildInstrumenter(spanKindExtractor);
    }
  }

  @SuppressWarnings("unchecked")
  public static <REQUEST, RESPONSE> Instrumenter<REQUEST, RESPONSE> buildDownstreamInstrumenter(
      InstrumenterBuilder<REQUEST, RESPONSE> builder,
      TextMapSetter<REQUEST> setter,
      SpanKindExtractor<REQUEST> spanKindExtractor) {
    if (buildDownstreamInstrumenterMethod == null) {
      logger.log(
          Level.WARNING,
          "Could not get InstrumenterBuilder#buildDownstreamInstrumenter() method with reflection");
      return builder.buildInstrumenter(spanKindExtractor);
    }
    try {
      return (Instrumenter<REQUEST, RESPONSE>)
          buildDownstreamInstrumenterMethod.invoke(builder, setter, spanKindExtractor);
    } catch (InvocationTargetException | IllegalAccessException e) {
      logger.log(
          Level.WARNING,
          "Error occurred when calling InstrumenterBuilder#buildDownstreamInstrumenter()",
          e);
      return builder.buildInstrumenter(spanKindExtractor);
    }
  }

  private InstrumenterUtil() {}
}
