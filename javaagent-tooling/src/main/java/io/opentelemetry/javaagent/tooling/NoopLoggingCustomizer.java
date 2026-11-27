/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.bootstrap.InternalLogger;
import javax.annotation.Nullable;

@AutoService(LoggingCustomizer.class)
public final class NoopLoggingCustomizer implements LoggingCustomizer {

  @Override
  public String name() {
    return "none";
  }

  @Override
  public void init() {
    // discards anything logged before this point
    InternalLogger.initialize(NoopLogger::new);
  }

  @Override
  @SuppressWarnings("SystemOut")
  public void onStartupFailure(Throwable throwable) {
    // there's no logging implementation installed, just print out the exception
    System.err.println("OpenTelemetry Javaagent failed to start");
    throwable.printStackTrace();
  }

  @Override
  public void onStartupSuccess() {}

  private static final class NoopLogger implements InternalLogger {

    private final String name;

    private NoopLogger(String name) {
      this.name = name;
    }

    @Override
    public boolean isLoggable(Level level) {
      return false;
    }

    @Override
    public void log(Level level, String message, @Nullable Throwable error) {}

    @Override
    public String name() {
      return name;
    }
  }
}
