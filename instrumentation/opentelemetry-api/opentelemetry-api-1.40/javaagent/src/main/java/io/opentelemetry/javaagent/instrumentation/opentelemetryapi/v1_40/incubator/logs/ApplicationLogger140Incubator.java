/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_40.incubator.logs;

import application.io.opentelemetry.api.incubator.logs.ExtendedLogger;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_27.logs.ApplicationLogger;

public class ApplicationLogger140Incubator extends ApplicationLogger implements ExtendedLogger {

  private final io.opentelemetry.api.logs.Logger agentLogger;

  protected ApplicationLogger140Incubator(io.opentelemetry.api.logs.Logger agentLogger) {
    super(agentLogger);
    this.agentLogger = agentLogger;
  }

  @Override
  public boolean isEnabled() {
    return ((io.opentelemetry.api.incubator.logs.ExtendedLogger) agentLogger)
        .isEnabled(Severity.UNDEFINED_SEVERITY_NUMBER);
  }
}
