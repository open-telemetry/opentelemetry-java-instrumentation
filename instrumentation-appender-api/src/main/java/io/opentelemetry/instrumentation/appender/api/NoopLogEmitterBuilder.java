/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.appender.api;

final class NoopLogEmitterBuilder implements LogEmitterBuilder {

  static final LogEmitterBuilder INSTANCE = new NoopLogEmitterBuilder();

  @Override
  public LogEmitterBuilder setSchemaUrl(String schemaUrl) {
    return this;
  }

  @Override
  public LogEmitterBuilder setInstrumentationVersion(String instrumentationVersion) {
    return this;
  }

  @Override
  public LogEmitter build() {
    return NoopLogEmitter.INSTANCE;
  }
}
