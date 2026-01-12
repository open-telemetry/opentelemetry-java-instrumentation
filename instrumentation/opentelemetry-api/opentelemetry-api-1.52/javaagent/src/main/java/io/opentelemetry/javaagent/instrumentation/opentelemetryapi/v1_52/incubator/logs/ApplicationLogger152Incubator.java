/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_52.incubator.logs;

import application.io.opentelemetry.api.incubator.logs.ExtendedLogger;
import application.io.opentelemetry.api.logs.Severity;
import application.io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.context.AgentContextStorage;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_27.logs.LogBridging;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_50.incubator.logs.ApplicationLogger150Incubator;

@SuppressWarnings("deprecation") // isEnabled() in ExtendedLogger has been deprecated
class ApplicationLogger152Incubator extends ApplicationLogger150Incubator
    implements ExtendedLogger {

  private final io.opentelemetry.api.incubator.logs.ExtendedLogger agentLogger;

  ApplicationLogger152Incubator(io.opentelemetry.api.logs.Logger agentLogger) {
    super(agentLogger);
    this.agentLogger = (io.opentelemetry.api.incubator.logs.ExtendedLogger) agentLogger;
  }

  @Override
  public boolean isEnabled(Severity severity, Context applicationContext) {
    return agentLogger.isEnabled(
        LogBridging.toAgent(severity), AgentContextStorage.getAgentContext(applicationContext));
  }

  @Override
  public boolean isEnabled(Severity severity) {
    return agentLogger.isEnabled(LogBridging.toAgent(severity));
  }
}
