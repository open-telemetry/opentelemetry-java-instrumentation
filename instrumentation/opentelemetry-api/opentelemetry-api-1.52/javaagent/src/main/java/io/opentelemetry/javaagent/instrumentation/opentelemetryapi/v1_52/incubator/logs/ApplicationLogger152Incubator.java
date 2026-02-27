/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_52.incubator.logs;

import io.opentelemetry.api.incubator.logs.ExtendedLogger;
import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.context.AgentContextStorage;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_27.logs.LogBridging;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_50.incubator.logs.ApplicationLogger150Incubator;

@SuppressWarnings("deprecation") // isEnabled() in ExtendedLogger has been deprecated
class ApplicationLogger152Incubator extends ApplicationLogger150Incubator
    implements application.io.opentelemetry.api.incubator.logs.ExtendedLogger {

  private final ExtendedLogger agentLogger;

  ApplicationLogger152Incubator(Logger agentLogger) {
    super(agentLogger);
    this.agentLogger = (ExtendedLogger) agentLogger;
  }

  @Override
  public boolean isEnabled(
      application.io.opentelemetry.api.logs.Severity severity,
      application.io.opentelemetry.context.Context applicationContext) {
    return agentLogger.isEnabled(
        LogBridging.toAgent(severity), AgentContextStorage.getAgentContext(applicationContext));
  }

  @Override
  public boolean isEnabled(application.io.opentelemetry.api.logs.Severity severity) {
    return agentLogger.isEnabled(LogBridging.toAgent(severity));
  }
}
