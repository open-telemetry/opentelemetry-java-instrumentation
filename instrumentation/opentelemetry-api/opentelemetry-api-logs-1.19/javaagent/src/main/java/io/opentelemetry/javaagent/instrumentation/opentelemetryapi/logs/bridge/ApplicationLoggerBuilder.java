/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.logs.bridge;

import application.io.opentelemetry.api.logs.Logger;
import application.io.opentelemetry.api.logs.LoggerBuilder;
import com.google.errorprone.annotations.CanIgnoreReturnValue;

final class ApplicationLoggerBuilder implements LoggerBuilder {

  private final io.opentelemetry.api.logs.LoggerBuilder agentBuilder;

  ApplicationLoggerBuilder(io.opentelemetry.api.logs.LoggerBuilder agentBuilder) {
    this.agentBuilder = agentBuilder;
  }

  @Override
  @CanIgnoreReturnValue
  public LoggerBuilder setEventDomain(String eventDomain) {
    agentBuilder.setEventDomain(eventDomain);
    return this;
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
    return new ApplicationLogger(agentBuilder.build());
  }
}
