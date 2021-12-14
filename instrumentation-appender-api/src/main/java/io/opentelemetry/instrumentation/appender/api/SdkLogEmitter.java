/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.appender.api;

final class SdkLogEmitter implements LogEmitter {

  private final io.opentelemetry.sdk.logs.LogEmitter delegate;

  SdkLogEmitter(io.opentelemetry.sdk.logs.LogEmitter delegate) {
    this.delegate = delegate;
  }

  @Override
  public LogBuilder logBuilder() {
    return new SdkLogBuilder(delegate.logBuilder());
  }
}
