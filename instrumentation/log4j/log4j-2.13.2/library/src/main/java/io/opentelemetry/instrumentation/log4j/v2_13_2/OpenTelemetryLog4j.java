/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.log4j.v2_13_2;

import io.opentelemetry.sdk.logs.SdkLogEmitterProvider;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;

public final class OpenTelemetryLog4j {

  private static final Object lock = new Object();

  @GuardedBy("lock")
  private static SdkLogEmitterProvider sdkLogEmitterProvider;

  @GuardedBy("lock")
  @Nullable
  private static Throwable initializeCaller;

  @GuardedBy("lock")
  private static final List<OpenTelemetryAppender> APPENDERS = new ArrayList<>();

  public static void initialize(SdkLogEmitterProvider sdkLogEmitterProvider) {
    List<OpenTelemetryAppender> instances;
    synchronized (lock) {
      if (OpenTelemetryLog4j.sdkLogEmitterProvider != null) {
        throw new IllegalStateException(
            "OpenTelemetryLog4j.initialize has already been called. OpenTelemetryLog4j.initialize "
                + "must be called only once. Previous invocation set to cause of this exception.",
            initializeCaller);
      }
      OpenTelemetryLog4j.sdkLogEmitterProvider = sdkLogEmitterProvider;
      instances = new ArrayList<>(APPENDERS);
      initializeCaller = new Throwable();
    }
    for (OpenTelemetryAppender instance : instances) {
      instance.initialize(sdkLogEmitterProvider);
    }
  }

  static void registerInstance(OpenTelemetryAppender appender) {
    synchronized (lock) {
      if (sdkLogEmitterProvider != null) {
        appender.initialize(sdkLogEmitterProvider);
      }
      APPENDERS.add(appender);
    }
  }

  // Visible for testing
  static void resetForTest() {
    synchronized (lock) {
      sdkLogEmitterProvider = null;
      APPENDERS.clear();
    }
  }

  private OpenTelemetryLog4j() {}
}
