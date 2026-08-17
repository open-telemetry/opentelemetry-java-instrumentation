/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_63.incubator.logs;

import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_27.logs.ApplicationLogger;

public class ApplicationLogger163Incubator extends ApplicationLogger
    implements application.io.opentelemetry.api.incubator.logs.ExtendedLogger {

  private final Logger agentLogger;

  public ApplicationLogger163Incubator(Logger agentLogger) {
    super(agentLogger);
    this.agentLogger = agentLogger;
  }

  @Override
  public application.io.opentelemetry.api.incubator.logs.ExtendedLogRecordBuilder
      logRecordBuilder() {
    return new ApplicationLogRecordBuilder163Incubator(agentLogger.logRecordBuilder());
  }
}
