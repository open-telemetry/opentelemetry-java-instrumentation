/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.appender.api;

final class NoopLogEmitterProvider implements LogEmitterProvider {

  static final NoopLogEmitterProvider INSTANCE = new NoopLogEmitterProvider();

  @Override
  public LogEmitterBuilder logEmitterBuilder(String instrumentationName) {
    return NoopLogEmitterBuilder.INSTANCE;
  }
}
