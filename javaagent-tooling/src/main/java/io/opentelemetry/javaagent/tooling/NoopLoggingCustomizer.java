/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

import com.google.auto.service.AutoService;

@AutoService(LoggingCustomizer.class)
public final class NoopLoggingCustomizer implements LoggingCustomizer {

  @Override
  public String name() {
    return "none";
  }

  @Override
  public void init() {}

  @Override
  @SuppressWarnings("SystemOut")
  public void onStartupFailure(Throwable throwable) {
    // there's no logging implementation installed, just print out the exception
    System.err.println("OpenTelemetry Javaagent failed to start");
    throwable.printStackTrace();
  }

  @Override
  public void onStartupSuccess() {}
}
