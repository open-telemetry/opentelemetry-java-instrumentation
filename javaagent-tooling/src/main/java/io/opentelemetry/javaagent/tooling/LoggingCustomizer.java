/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;

// only one LoggingCustomizer is allowed, and its presence will suppress the
// DefaultLoggingCustomizer
public interface LoggingCustomizer {

  void init();

  /**
   * Register a callback which will be called on synchronous startup success.
   *
   * <p>Synchronous startup may or may not include running {@link
   * io.opentelemetry.javaagent.extension.AgentListener#afterAgent(Config,
   * AutoConfiguredOpenTelemetrySdk)}" listeners.
   */
  void onStartupSuccess();

  /**
   * Register a callback which will be called on synchronous startup failure (including if {@link
   * #init()} fails).
   *
   * <p>Synchronous startup may or may not include running {@link
   * io.opentelemetry.javaagent.extension.AgentListener#afterAgent(Config,
   * AutoConfiguredOpenTelemetrySdk)}" listeners.
   */
  void onStartupFailure(Throwable throwable);
}
