/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.log4j.v2_16;

import io.opentelemetry.instrumentation.appender.api.LogEmitterProvider;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;

public final class OpenTelemetryLog4j {

  private static final Object lock = new Object();

  @GuardedBy("lock")
  private static LogEmitterProvider logEmitterProvider = LogEmitterProvider.noop();

  @GuardedBy("lock")
  @Nullable
  private static Throwable initializeCaller;

  @GuardedBy("lock")
  private static final List<OpenTelemetryAppender> APPENDERS = new ArrayList<>();

  public static void initialize(LogEmitterProvider logEmitterProvider) {
    List<OpenTelemetryAppender> instances;
    synchronized (lock) {
      if (OpenTelemetryLog4j.logEmitterProvider != LogEmitterProvider.noop()) {
        throw new IllegalStateException(
            "OpenTelemetryLog4j.initialize has already been called. OpenTelemetryLog4j.initialize "
                + "must be called only once. Previous invocation set to cause of this exception.",
            initializeCaller);
      }
      OpenTelemetryLog4j.logEmitterProvider = logEmitterProvider;
      instances = new ArrayList<>(APPENDERS);
      initializeCaller = new Throwable();
    }
    for (OpenTelemetryAppender instance : instances) {
      instance.initialize(logEmitterProvider);
    }
  }

  static void registerInstance(OpenTelemetryAppender appender) {
    synchronized (lock) {
      if (logEmitterProvider != LogEmitterProvider.noop()) {
        appender.initialize(logEmitterProvider);
      }
      APPENDERS.add(appender);
    }
  }

  // Visible for testing
  static void resetForTest() {
    synchronized (lock) {
      logEmitterProvider = LogEmitterProvider.noop();
      APPENDERS.clear();
    }
  }

  private OpenTelemetryLog4j() {}
}
