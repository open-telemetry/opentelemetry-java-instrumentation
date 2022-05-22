/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.appender.internal;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public interface LogEmitterProvider {

  /**
   * Creates a {@link LogEmitterBuilder} instance.
   *
   * @param instrumentationName the name of the instrumentation library
   * @return a log emitter builder instance
   */
  LogEmitterBuilder logEmitterBuilder(String instrumentationName);
}
