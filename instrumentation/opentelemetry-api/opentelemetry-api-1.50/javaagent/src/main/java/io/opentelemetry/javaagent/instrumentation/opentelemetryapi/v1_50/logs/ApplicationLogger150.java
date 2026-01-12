/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_50.logs;

import application.io.opentelemetry.api.logs.LogRecordBuilder;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_27.logs.ApplicationLogger;

class ApplicationLogger150 extends ApplicationLogger {

  private final io.opentelemetry.api.logs.Logger agentLogger;

  ApplicationLogger150(io.opentelemetry.api.logs.Logger agentLogger) {
    super(agentLogger);
    this.agentLogger = agentLogger;
  }

  @Override
  public LogRecordBuilder logRecordBuilder() {
    return new ApplicationLogRecordBuilder150(agentLogger.logRecordBuilder());
  }
}
