/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ratpack.v1_7.internal;

import static java.util.logging.Level.FINE;

import io.opentelemetry.instrumentation.ratpack.v1_7.RatpackClientTelemetryBuilder;
import io.opentelemetry.instrumentation.ratpack.v1_7.RatpackServerTelemetryBuilder;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.logging.Logger;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
// TODO (trask) update the above javadoc similar to
//  https://github.com/open-telemetry/opentelemetry-java/pull/6886
public class Experimental {

  private static final Logger logger = Logger.getLogger(Experimental.class.getName());

  @Nullable
  private static final Method emitExperimentalClientTelemetryMethod =
      getEmitExperimentalClientTelemetryMethod();

  @Nullable
  private static final Method emitExperimentalServerTelemetryMethod =
      getEmitExperimentalServerTelemetryMethod();

  public void setEmitExperimentalTelemetry(
      RatpackClientTelemetryBuilder builder, boolean emitExperimentalTelemetry) {

    if (emitExperimentalClientTelemetryMethod != null) {
      try {
        emitExperimentalClientTelemetryMethod.invoke(builder, emitExperimentalTelemetry);
      } catch (IllegalAccessException | InvocationTargetException e) {
        logger.log(FINE, e.getMessage(), e);
      }
    }
  }

  public void setEmitExperimentalTelemetry(
      RatpackServerTelemetryBuilder builder, boolean emitExperimentalTelemetry) {

    if (emitExperimentalServerTelemetryMethod != null) {
      try {
        emitExperimentalServerTelemetryMethod.invoke(builder, emitExperimentalTelemetry);
      } catch (IllegalAccessException | InvocationTargetException e) {
        logger.log(FINE, e.getMessage(), e);
      }
    }
  }

  @Nullable
  private static Method getEmitExperimentalClientTelemetryMethod() {
    try {
      Method method =
          RatpackClientTelemetryBuilder.class.getDeclaredMethod(
              "setEmitExperimentalHttpClientMetrics", boolean.class);
      method.setAccessible(true);
      return method;
    } catch (NoSuchMethodException e) {
      logger.log(FINE, e.getMessage(), e);
      return null;
    }
  }

  @Nullable
  private static Method getEmitExperimentalServerTelemetryMethod() {
    try {
      Method method =
          RatpackServerTelemetryBuilder.class.getDeclaredMethod(
              "setEmitExperimentalHttpServerMetrics", boolean.class);
      method.setAccessible(true);
      return method;
    } catch (NoSuchMethodException e) {
      logger.log(FINE, e.getMessage(), e);
      return null;
    }
  }
}
