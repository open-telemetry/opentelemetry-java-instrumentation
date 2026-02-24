/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_27.logs;

import application.io.opentelemetry.api.logs.LogRecordBuilder;
import application.io.opentelemetry.api.logs.Logger;

public class ApplicationLogger implements Logger {

  private final io.opentelemetry.api.logs.Logger agentLogger;

  protected ApplicationLogger(io.opentelemetry.api.logs.Logger agentLogger) {
    this.agentLogger = agentLogger;
  }

  @Override
  public LogRecordBuilder logRecordBuilder() {
    return new ApplicationLogRecordBuilder(agentLogger.logRecordBuilder());
  }
}
