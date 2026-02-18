/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_27.logs;

import io.opentelemetry.api.logs.LoggerProvider;

public class ApplicationLoggerProvider
    implements application.io.opentelemetry.api.logs.LoggerProvider {

  private final ApplicationLoggerFactory loggerFactory;
  private final LoggerProvider agentLoggerProvider;

  public ApplicationLoggerProvider(
      ApplicationLoggerFactory loggerFactory, LoggerProvider agentLoggerProvider) {
    this.loggerFactory = loggerFactory;
    this.agentLoggerProvider = agentLoggerProvider;
  }

  @Override
  public application.io.opentelemetry.api.logs.LoggerBuilder loggerBuilder(
      String instrumentationName) {
    return new ApplicationLoggerBuilder(
        loggerFactory, agentLoggerProvider.loggerBuilder(instrumentationName));
  }
}
