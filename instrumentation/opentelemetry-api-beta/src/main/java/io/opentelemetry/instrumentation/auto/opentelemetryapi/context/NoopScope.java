/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.opentelemetryapi.context;

import application.io.opentelemetry.context.Scope;

/** A {@link Scope} that does nothing when it is created or closed. */
public final class NoopScope implements Scope {
  /**
   * Returns the singleton instance of {@code NoopScope}.
   *
   * @return the singleton instance of {@code NoopScope}.
   * @since 0.1.0
   */
  public static Scope getInstance() {
    return INSTANCE;
  }

  private static final Scope INSTANCE = new NoopScope();

  private NoopScope() {}

  @Override
  public void close() {}
}
