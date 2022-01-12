/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.appender.internal;

final class NoopLogEmitter implements LogEmitter {

  static final LogEmitter INSTANCE = new NoopLogEmitter();

  @Override
  public LogBuilder logBuilder() {
    return NoopLogBuilder.INSTANCE;
  }
}
