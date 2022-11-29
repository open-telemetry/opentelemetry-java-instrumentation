/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.logging.application;

import io.opentelemetry.javaagent.bootstrap.InternalLogger;
import javax.annotation.Nullable;

final class ApplicationLogger extends InternalLogger {

  private final InMemoryLogStore inMemoryLogStore;
  private final String name;
  private volatile InternalLogger actual;

  ApplicationLogger(InMemoryLogStore inMemoryLogStore, String name) {
    this.inMemoryLogStore = inMemoryLogStore;
    this.name = name;
  }

  @Override
  public boolean isLoggable(Level level) {
    InternalLogger actual = this.actual;
    if (actual == null) {
      return true;
    }
    return actual.isLoggable(level);
  }

  @Override
  public void log(Level level, String message, @Nullable Throwable error) {
    InternalLogger actual = this.actual;
    if (actual == null) {
      inMemoryLogStore.write(new InMemoryLog(name, level, message, error));
      return;
    }
    actual.log(level, message, error);
  }

  @Override
  public String name() {
    return name;
  }

  void replaceByActualLogger(InternalLogger bridgedApplicationLogger) {
    actual = bridgedApplicationLogger;
  }
}
