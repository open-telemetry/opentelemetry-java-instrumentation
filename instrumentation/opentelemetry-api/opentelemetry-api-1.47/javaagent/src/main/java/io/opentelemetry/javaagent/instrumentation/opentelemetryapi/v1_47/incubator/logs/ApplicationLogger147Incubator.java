/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_47.incubator.logs;

import io.opentelemetry.api.incubator.logs.ExtendedLogger;
import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_27.logs.ApplicationLogger;

class ApplicationLogger147Incubator extends ApplicationLogger
    implements application.io.opentelemetry.api.incubator.logs.ExtendedLogger {

  private final Logger agentLogger;

  ApplicationLogger147Incubator(Logger agentLogger) {
    super(agentLogger);
    this.agentLogger = agentLogger;
  }

  @Override
  public boolean isEnabled() {
    return ((ExtendedLogger) agentLogger).isEnabled(Severity.UNDEFINED_SEVERITY_NUMBER);
  }

  @Override
  public application.io.opentelemetry.api.incubator.logs.ExtendedLogRecordBuilder
      logRecordBuilder() {
    return new ApplicationLogRecordBuilder147Incubator(agentLogger.logRecordBuilder());
  }
}
