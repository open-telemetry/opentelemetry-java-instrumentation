/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.appender;

final class DelegatingLogEmitter implements LogEmitter {

  private final io.opentelemetry.sdk.logs.LogEmitter delegate;

  DelegatingLogEmitter(io.opentelemetry.sdk.logs.LogEmitter delegate) {
    this.delegate = delegate;
  }

  @Override
  public LogBuilder logBuilder() {
    return new DelegatingLogBuilder(delegate.logBuilder());
  }
}
