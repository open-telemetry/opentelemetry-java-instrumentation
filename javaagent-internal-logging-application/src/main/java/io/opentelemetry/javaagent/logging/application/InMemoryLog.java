/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.logging.application;

import com.google.auto.value.AutoValue;
import io.opentelemetry.javaagent.bootstrap.InternalLogger;
import java.io.PrintStream;
import javax.annotation.Nullable;

@AutoValue
abstract class InMemoryLog {

  abstract String name();

  abstract InternalLogger.Level level();

  abstract String message();

  @Nullable
  abstract Throwable error();

  static InMemoryLog create(
      String name, InternalLogger.Level level, String message, @Nullable Throwable error) {
    return new AutoValue_InMemoryLog(name, level, message, error);
  }

  void dump(PrintStream out) {
    out.print("[otel.javaagent] ");
    out.print(level());
    out.print(" ");
    out.print(name());
    out.print(" - ");
    out.println(message());
    Throwable error = error();
    if (error != null) {
      error.printStackTrace(out);
    }
  }
}
