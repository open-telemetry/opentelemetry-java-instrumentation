/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_52.incubator.logs;

import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_27.logs.ApplicationLogger;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_27.logs.ApplicationLoggerFactory;

// this class is used from opentelemetry-api-1.27.0 via reflection
public class ApplicationLoggerFactory152Incubator implements ApplicationLoggerFactory {

  @Override
  public ApplicationLogger newLogger(Logger agentLogger) {
    return new ApplicationLogger152Incubator(agentLogger);
  }
}
