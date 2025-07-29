/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_27.logs;

import io.opentelemetry.api.logs.Logger;

public class ApplicationLoggerFactory127 implements ApplicationLoggerFactory {

  @Override
  public ApplicationLogger newLogger(Logger agentLogger) {
    return new ApplicationLogger(agentLogger);
  }
}
