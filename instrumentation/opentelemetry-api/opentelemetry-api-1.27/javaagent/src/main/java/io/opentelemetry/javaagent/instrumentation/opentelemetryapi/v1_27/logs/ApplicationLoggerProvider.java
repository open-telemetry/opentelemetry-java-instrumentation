/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_27.logs;

import application.io.opentelemetry.api.logs.LoggerBuilder;
import application.io.opentelemetry.api.logs.LoggerProvider;

// Our convention for accessing agent packages.
@SuppressWarnings("UnnecessarilyFullyQualified")
public class ApplicationLoggerProvider implements LoggerProvider {

  private final io.opentelemetry.api.logs.LoggerProvider agentLoggerProvider;

  public ApplicationLoggerProvider(io.opentelemetry.api.logs.LoggerProvider agentLoggerProvider) {
    this.agentLoggerProvider = agentLoggerProvider;
  }

  @Override
  public LoggerBuilder loggerBuilder(String instrumentationName) {
    return new ApplicationLoggerBuilder(agentLoggerProvider.loggerBuilder(instrumentationName));
  }
}
