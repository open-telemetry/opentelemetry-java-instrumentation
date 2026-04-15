/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_27.logs;

import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.context.AgentContextStorage;

public class ApplicationLogger implements application.io.opentelemetry.api.logs.Logger {

  private final Logger agentLogger;

  protected ApplicationLogger(Logger agentLogger) {
    this.agentLogger = agentLogger;
  }

  @Override
  public application.io.opentelemetry.api.logs.LogRecordBuilder logRecordBuilder() {
    return new ApplicationLogRecordBuilder(agentLogger.logRecordBuilder());
  }

  // added in 1.52.0 to incubator api
  // added in 1.61.0 to stable api
  public boolean isEnabled(
      application.io.opentelemetry.api.logs.Severity severity,
      application.io.opentelemetry.context.Context applicationContext) {
    return agentLogger.isEnabled(
        LogBridging.toAgent(severity), AgentContextStorage.getAgentContext(applicationContext));
  }

  // added in 1.52.0 to incubator api
  // added in 1.61.0 to stable api
  public boolean isEnabled(application.io.opentelemetry.api.logs.Severity severity) {
    return agentLogger.isEnabled(LogBridging.toAgent(severity));
  }
}
