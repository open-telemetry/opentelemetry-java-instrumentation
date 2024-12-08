/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachehttpclient.v5_2.internal;

import static java.util.logging.Level.FINE;

import io.opentelemetry.instrumentation.apachehttpclient.v5_2.ApacheHttpClientTelemetryBuilder;
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
  private static final Method emitExperimentalHttpClientMetricsMethod =
      getEmitExperimentalHttpClientMetricsMethod();

  public void setEmitExperimentalHttpClientMetrics(
      ApacheHttpClientTelemetryBuilder builder, boolean emitExperimentalHttpClientMetrics) {

    if (emitExperimentalHttpClientMetricsMethod != null) {
      try {
        emitExperimentalHttpClientMetricsMethod.invoke(builder, emitExperimentalHttpClientMetrics);
      } catch (IllegalAccessException | InvocationTargetException e) {
        logger.log(FINE, e.getMessage(), e);
      }
    }
  }

  @Nullable
  private static Method getEmitExperimentalHttpClientMetricsMethod() {
    try {
      return ApacheHttpClientTelemetryBuilder.class.getMethod(
          "setEmitExperimentalHttpClientMetrics", boolean.class);
    } catch (NoSuchMethodException e) {
      logger.log(FINE, e.getMessage(), e);
      return null;
    }
  }
}
