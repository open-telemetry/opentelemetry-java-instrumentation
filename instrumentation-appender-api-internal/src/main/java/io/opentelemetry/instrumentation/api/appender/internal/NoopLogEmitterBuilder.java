/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.appender.internal;

import com.google.errorprone.annotations.CanIgnoreReturnValue;

final class NoopLogEmitterBuilder implements LogEmitterBuilder {

  static final LogEmitterBuilder INSTANCE = new NoopLogEmitterBuilder();

  @Override
  @CanIgnoreReturnValue
  public LogEmitterBuilder setSchemaUrl(String schemaUrl) {
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public LogEmitterBuilder setInstrumentationVersion(String instrumentationVersion) {
    return this;
  }

  @Override
  public LogEmitter build() {
    return NoopLogEmitter.INSTANCE;
  }
}
