/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class InstrumenterUtil {

  private static final Logger logger = Logger.getLogger(InstrumenterUtil.class.getName());

  public static <REQUEST, RESPONSE> Context startAndEnd(
      Instrumenter<REQUEST, RESPONSE> instrumenter,
      Context parentContext,
      REQUEST request,
      @Nullable RESPONSE response,
      @Nullable Throwable error,
      long startTimeNanos,
      long endTimeNanos) {

    try {
      Method method =
          Instrumenter.class.getDeclaredMethod(
              "startAndEnd",
              Context.class,
              Object.class,
              Object.class,
              Throwable.class,
              long.class,
              long.class);
      method.setAccessible(true);
      return (Context)
          method.invoke(
              instrumenter, parentContext, request, response, error, startTimeNanos, endTimeNanos);
    } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
      logger.log(Level.WARNING, "Error occurred when calling Instrumenter#startAndEnd()", e);
      return parentContext;
    }
  }

  private InstrumenterUtil() {}
}
