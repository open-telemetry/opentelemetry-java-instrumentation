/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.armeria.v1_3.internal;

import static java.util.logging.Level.FINE;

import io.opentelemetry.instrumentation.armeria.v1_3.ArmeriaClientTelemetryBuilder;
import io.opentelemetry.instrumentation.armeria.v1_3.ArmeriaServerTelemetryBuilder;
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

  @Nullable private static final Method emitClientPeerServiceMethod = getClientPeerServiceMethod();

  @Nullable
  private static final Method emitExperimentalHttpServerMetricsMethod =
      getEmitExperimentalHttpServerMetricsMethod();

  public void setEmitExperimentalHttpClientMetrics(
      ArmeriaClientTelemetryBuilder builder, boolean emitExperimentalHttpClientMetrics) {

    if (emitExperimentalHttpClientMetricsMethod != null) {
      try {
        emitExperimentalHttpClientMetricsMethod.invoke(builder, emitExperimentalHttpClientMetrics);
      } catch (IllegalAccessException | InvocationTargetException e) {
        logger.log(FINE, e.getMessage(), e);
      }
    }
  }

  public void setEmitExperimentalHttpServerMetrics(
      ArmeriaServerTelemetryBuilder builder, boolean emitExperimentalHttpServerMetrics) {

    if (emitExperimentalHttpServerMetricsMethod != null) {
      try {
        emitExperimentalHttpServerMetricsMethod.invoke(builder, emitExperimentalHttpServerMetrics);
      } catch (IllegalAccessException | InvocationTargetException e) {
        logger.log(FINE, e.getMessage(), e);
      }
    }
  }

  public void setClientPeerService(ArmeriaClientTelemetryBuilder builder, String peerService) {

    if (emitClientPeerServiceMethod != null) {
      try {
        emitClientPeerServiceMethod.invoke(builder, peerService);
      } catch (IllegalAccessException | InvocationTargetException e) {
        logger.log(FINE, e.getMessage(), e);
      }
    }
  }

  @Nullable
  private static Method getEmitExperimentalHttpClientMetricsMethod() {
    try {
      return ArmeriaClientTelemetryBuilder.class.getMethod(
          "setEmitExperimentalHttpClientMetrics", boolean.class);
    } catch (NoSuchMethodException e) {
      logger.log(FINE, e.getMessage(), e);
      return null;
    }
  }

  @Nullable
  private static Method getClientPeerServiceMethod() {
    try {
      return ArmeriaClientTelemetryBuilder.class.getMethod("setPeerService", String.class);
    } catch (NoSuchMethodException e) {
      logger.log(FINE, e.getMessage(), e);
      return null;
    }
  }

  @Nullable
  private static Method getEmitExperimentalHttpServerMetricsMethod() {
    try {
      return ArmeriaServerTelemetryBuilder.class.getMethod(
          "setEmitExperimentalHttpServerMetrics", boolean.class);
    } catch (NoSuchMethodException e) {
      logger.log(FINE, e.getMessage(), e);
      return null;
    }
  }
}
