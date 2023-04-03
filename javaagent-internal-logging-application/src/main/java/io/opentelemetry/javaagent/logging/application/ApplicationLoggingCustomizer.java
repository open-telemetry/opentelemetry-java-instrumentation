/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.logging.application;

import com.google.auto.service.AutoService;
import io.opentelemetry.instrumentation.api.internal.ConfigPropertiesUtil;
import io.opentelemetry.javaagent.bootstrap.InternalLogger;
import io.opentelemetry.javaagent.bootstrap.logging.ApplicationLoggerBridge;
import io.opentelemetry.javaagent.tooling.LoggingCustomizer;

@AutoService(LoggingCustomizer.class)
public final class ApplicationLoggingCustomizer implements LoggingCustomizer {

  @Override
  public String name() {
    return "application";
  }

  @Override
  public void init() {
    int limit =
        ConfigPropertiesUtil.getInt(
            "otel.javaagent.logging.application.logs-buffer-max-records", 2048);
    InMemoryLogStore inMemoryLogStore = new InMemoryLogStore(limit);
    ApplicationLoggerFactory loggerFactory = new ApplicationLoggerFactory(inMemoryLogStore);
    // register a shutdown hook that'll dump the logs to stderr in case something goes wrong
    Runtime.getRuntime().addShutdownHook(new Thread(() -> inMemoryLogStore.dump(System.err)));
    ApplicationLoggerBridge.set(loggerFactory);
    InternalLogger.initialize(loggerFactory);
  }

  @Override
  public void onStartupSuccess() {}

  @Override
  @SuppressWarnings("SystemOut")
  public void onStartupFailure(Throwable throwable) {
    // most likely the application bridge wasn't initialized, let's just print
    System.err.println("OpenTelemetry Javaagent failed to start");
    throwable.printStackTrace();
  }
}
