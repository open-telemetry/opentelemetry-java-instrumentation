/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_63.logs;

import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_27.logs.ApplicationLogger;

class ApplicationLogger163 extends ApplicationLogger {

  private final Logger agentLogger;

  ApplicationLogger163(Logger agentLogger) {
    super(agentLogger);
    this.agentLogger = agentLogger;
  }

  @Override
  public application.io.opentelemetry.api.logs.LogRecordBuilder logRecordBuilder() {
    return new ApplicationLogRecordBuilder163(agentLogger.logRecordBuilder());
  }
}
