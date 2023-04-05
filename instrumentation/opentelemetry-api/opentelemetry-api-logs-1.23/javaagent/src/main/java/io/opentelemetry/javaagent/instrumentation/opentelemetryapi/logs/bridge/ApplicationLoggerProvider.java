/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.logs.bridge;

import application.io.opentelemetry.api.logs.LoggerBuilder;
import application.io.opentelemetry.api.logs.LoggerProvider;

// Our convention for accessing agent packages.
@SuppressWarnings("UnnecessarilyFullyQualified")
public class ApplicationLoggerProvider implements LoggerProvider {

  public static final LoggerProvider INSTANCE = new ApplicationLoggerProvider();

  private final io.opentelemetry.api.logs.LoggerProvider agentLoggerProvider;

  public ApplicationLoggerProvider() {
    this.agentLoggerProvider = io.opentelemetry.api.logs.GlobalLoggerProvider.get();
  }

  @Override
  public LoggerBuilder loggerBuilder(String instrumentationName) {
    return new ApplicationLoggerBuilder(agentLoggerProvider.loggerBuilder(instrumentationName));
  }
}
