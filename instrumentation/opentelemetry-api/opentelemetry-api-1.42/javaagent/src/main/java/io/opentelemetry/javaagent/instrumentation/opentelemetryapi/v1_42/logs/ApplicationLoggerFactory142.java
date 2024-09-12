/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_42.logs;

import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_27.logs.ApplicationLogger;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_27.logs.ApplicationLoggerFactory;

public class ApplicationLoggerFactory142 implements ApplicationLoggerFactory {

  @Override
  public ApplicationLogger newLogger(Logger agentLogger) {
    return new ApplicationLogger142(agentLogger);
  }
}
