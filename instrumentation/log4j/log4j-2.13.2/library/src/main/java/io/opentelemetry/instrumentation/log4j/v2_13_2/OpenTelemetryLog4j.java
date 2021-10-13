/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.log4j.v2_13_2;

import io.opentelemetry.sdk.logging.LogSink;
import io.opentelemetry.sdk.resources.Resource;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.concurrent.GuardedBy;

public final class OpenTelemetryLog4j {

  private static final Object LOCK = new Object();

  @GuardedBy("LOCK")
  private static LogSink logSink;

  @GuardedBy("LOCK")
  private static Resource resource;

  @GuardedBy("LOCK")
  private static final List<OpenTelemetryAppender> APPENDERS = new ArrayList<>();

  public static void initialize(LogSink logSink, Resource resource) {
    List<OpenTelemetryAppender> instances;
    synchronized (LOCK) {
      if (OpenTelemetryLog4j.logSink != null) {
        throw new IllegalStateException("LogSinkSdkProvider has already been set.");
      }
      OpenTelemetryLog4j.logSink = logSink;
      OpenTelemetryLog4j.resource = resource;
      instances = new ArrayList<>(APPENDERS);
    }
    for (OpenTelemetryAppender instance : instances) {
      instance.initialize(logSink, resource);
    }
  }

  static void registerInstance(OpenTelemetryAppender appender) {
    synchronized (LOCK) {
      if (logSink != null) {
        appender.initialize(logSink, resource);
      }
      APPENDERS.add(appender);
    }
  }

  private OpenTelemetryLog4j() {}
}
