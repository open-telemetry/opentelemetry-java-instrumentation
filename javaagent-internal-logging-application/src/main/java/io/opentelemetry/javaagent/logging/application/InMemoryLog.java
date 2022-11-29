/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.logging.application;

import io.opentelemetry.javaagent.bootstrap.InternalLogger;
import java.io.PrintStream;
import java.util.Objects;
import javax.annotation.Nullable;

final class InMemoryLog {

  final String name;
  final InternalLogger.Level level;
  final String message;
  @Nullable final Throwable error;

  InMemoryLog(String name, InternalLogger.Level level, String message, @Nullable Throwable error) {
    this.name = name;
    this.level = level;
    this.message = message;
    this.error = error;
  }

  void dump(PrintStream out) {
    out.print("[otel.javaagent] ");
    out.print(level);
    out.print(" ");
    out.print(name);
    out.print(" - ");
    out.println(message);
    if (error != null) {
      error.printStackTrace(out);
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    InMemoryLog log = (InMemoryLog) o;
    return name.equals(log.name)
        && level == log.level
        && Objects.equals(message, log.message)
        && Objects.equals(error, log.error);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, level, message, error);
  }

  @Override
  public String toString() {
    return "InMemoryLog{"
        + "name='"
        + name
        + '\''
        + ", level="
        + level
        + ", message='"
        + message
        + '\''
        + ", error="
        + error
        + '}';
  }
}
