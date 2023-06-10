/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_27;

import application.io.opentelemetry.api.OpenTelemetry;
import application.io.opentelemetry.api.logs.LoggerProvider;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.ApplicationOpenTelemetry110;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_27.logs.ApplicationLoggerProvider;

public final class ApplicationOpenTelemetry127 extends ApplicationOpenTelemetry110 {

  // Accessed with reflection
  @SuppressWarnings("unused")
  public static final OpenTelemetry INSTANCE = new ApplicationOpenTelemetry127();

  private final LoggerProvider applicationLoggerProvider;

  @SuppressWarnings("UnnecessarilyFullyQualified")
  private ApplicationOpenTelemetry127() {
    io.opentelemetry.api.OpenTelemetry agentOpenTelemetry =
        io.opentelemetry.api.GlobalOpenTelemetry.get();
    applicationLoggerProvider = new ApplicationLoggerProvider(agentOpenTelemetry.getLogsBridge());
  }

  @Override
  public LoggerProvider getLogsBridge() {
    return applicationLoggerProvider;
  }
}
