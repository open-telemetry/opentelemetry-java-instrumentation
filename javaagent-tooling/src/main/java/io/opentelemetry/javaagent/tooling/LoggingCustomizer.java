/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;

// only one LoggingCustomizer is allowed, and its presence will suppress the
// DefaultLoggingCustomizer
public interface LoggingCustomizer {

  /**
   * Returns name of the logger implementation. Will be matched against the value of the {@code
   * otel.javavagent.logger} system property - in case it is the same, this {@link
   * LoggingCustomizer} will be used to initialize the javaagent logging subsystem.
   */
  String name();

  // note that if this throws an exception, it will end up calling onStartupFailure, because
  // otherwise that exception will bubble up to OpenTelemetryAgent where a distro cannot control the
  // logging of it.
  void init();

  /**
   * Register a callback which will be called on synchronous startup success.
   *
   * <p>Synchronous startup may or may not include running {@link
   * io.opentelemetry.javaagent.extension.AgentListener#afterAgent(
   * AutoConfiguredOpenTelemetrySdk)}" listeners.
   */
  void onStartupSuccess();

  /**
   * Register a callback which will be called on synchronous startup failure (including if {@link
   * #init()} fails).
   *
   * <p>Synchronous startup may or may not include running {@link
   * io.opentelemetry.javaagent.extension.AgentListener#afterAgent(
   * AutoConfiguredOpenTelemetrySdk)}" listeners.
   */
  void onStartupFailure(Throwable throwable);
}
