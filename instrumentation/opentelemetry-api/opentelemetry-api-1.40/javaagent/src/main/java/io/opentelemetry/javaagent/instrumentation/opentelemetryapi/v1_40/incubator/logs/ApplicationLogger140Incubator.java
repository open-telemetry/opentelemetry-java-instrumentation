/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_40.incubator.logs;

import io.opentelemetry.api.incubator.logs.ExtendedLogger;
import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_27.logs.ApplicationLogger;

public class ApplicationLogger140Incubator extends ApplicationLogger
    implements application.io.opentelemetry.api.incubator.logs.ExtendedLogger {

  private final Logger agentLogger;

  protected ApplicationLogger140Incubator(Logger agentLogger) {
    super(agentLogger);
    this.agentLogger = agentLogger;
  }

  @Override
  public boolean isEnabled() {
    return ((ExtendedLogger) agentLogger).isEnabled(Severity.UNDEFINED_SEVERITY_NUMBER);
  }
}
