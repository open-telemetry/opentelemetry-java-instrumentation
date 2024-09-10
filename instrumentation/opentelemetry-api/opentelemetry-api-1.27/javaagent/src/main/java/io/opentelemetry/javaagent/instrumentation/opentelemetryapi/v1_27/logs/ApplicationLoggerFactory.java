/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_27.logs;

public interface ApplicationLoggerFactory {

  ApplicationLogger newLogger(io.opentelemetry.api.logs.Logger agentLogger);
}
