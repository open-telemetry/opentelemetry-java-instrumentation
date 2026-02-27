/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_27.logs;

import io.opentelemetry.api.logs.Logger;

public class ApplicationLogger implements application.io.opentelemetry.api.logs.Logger {

  private final Logger agentLogger;

  protected ApplicationLogger(Logger agentLogger) {
    this.agentLogger = agentLogger;
  }

  @Override
  public application.io.opentelemetry.api.logs.LogRecordBuilder logRecordBuilder() {
    return new ApplicationLogRecordBuilder(agentLogger.logRecordBuilder());
  }
}
