/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_27.logs;

import application.io.opentelemetry.api.logs.Logger;
import application.io.opentelemetry.api.logs.LoggerBuilder;
import com.google.errorprone.annotations.CanIgnoreReturnValue;

final class ApplicationLoggerBuilder implements LoggerBuilder {

  private final ApplicationLoggerFactory loggerFactory;
  private final io.opentelemetry.api.logs.LoggerBuilder agentBuilder;

  ApplicationLoggerBuilder(
      ApplicationLoggerFactory loggerFactory,
      io.opentelemetry.api.logs.LoggerBuilder agentBuilder) {
    this.loggerFactory = loggerFactory;
    this.agentBuilder = agentBuilder;
  }

  @Override
  @CanIgnoreReturnValue
  public LoggerBuilder setSchemaUrl(String schemaUrl) {
    agentBuilder.setSchemaUrl(schemaUrl);
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public LoggerBuilder setInstrumentationVersion(String version) {
    agentBuilder.setInstrumentationVersion(version);
    return this;
  }

  @Override
  public Logger build() {
    return loggerFactory.newLogger(agentBuilder.build());
  }
}
