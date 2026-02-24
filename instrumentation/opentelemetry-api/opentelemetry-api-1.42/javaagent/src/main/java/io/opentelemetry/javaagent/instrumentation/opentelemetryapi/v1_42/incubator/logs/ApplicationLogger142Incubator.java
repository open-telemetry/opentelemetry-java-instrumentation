/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_42.incubator.logs;

import application.io.opentelemetry.api.logs.LogRecordBuilder;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_40.incubator.logs.ApplicationLogger140Incubator;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_42.logs.ApplicationLogRecordBuilder142;

class ApplicationLogger142Incubator extends ApplicationLogger140Incubator {

  private final io.opentelemetry.api.logs.Logger agentLogger;

  ApplicationLogger142Incubator(io.opentelemetry.api.logs.Logger agentLogger) {
    super(agentLogger);
    this.agentLogger = agentLogger;
  }

  @Override
  public LogRecordBuilder logRecordBuilder() {
    return new ApplicationLogRecordBuilder142(agentLogger.logRecordBuilder());
  }
}
