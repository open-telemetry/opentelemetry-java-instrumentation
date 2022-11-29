/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
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

  static {
    Method method = null;
    try {
      method =
          Instrumenter.class.getDeclaredMethod(
              "startAndEnd",
              Context.class,
              Object.class,
              Object.class,
              Throwable.class,
              Instant.class,
              Instant.class);
      method.setAccessible(true);
    } catch (NoSuchMethodException e) {
      logger.log(
          Level.WARNING, "Could not get Instrumenter#startAndEnd() method with reflection", e);
    }
    startAndEndMethod = method;
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
      // already logged a warning when this class initialized
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

  private InstrumenterUtil() {}
}
