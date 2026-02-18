/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_27.logs;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.logs.LoggerBuilder;

final class ApplicationLoggerBuilder
    implements application.io.opentelemetry.api.logs.LoggerBuilder {

  private final ApplicationLoggerFactory loggerFactory;
  private final LoggerBuilder agentBuilder;

  ApplicationLoggerBuilder(ApplicationLoggerFactory loggerFactory, LoggerBuilder agentBuilder) {
    this.loggerFactory = loggerFactory;
    this.agentBuilder = agentBuilder;
  }

  @Override
  @CanIgnoreReturnValue
  public application.io.opentelemetry.api.logs.LoggerBuilder setSchemaUrl(String schemaUrl) {
    agentBuilder.setSchemaUrl(schemaUrl);
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public application.io.opentelemetry.api.logs.LoggerBuilder setInstrumentationVersion(
      String version) {
    agentBuilder.setInstrumentationVersion(version);
    return this;
  }

  @Override
  public application.io.opentelemetry.api.logs.Logger build() {
    return loggerFactory.newLogger(agentBuilder.build());
  }
}
