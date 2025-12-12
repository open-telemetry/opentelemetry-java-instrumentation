/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.mongo.v3_7;

import com.mongodb.event.CommandListener;
import com.mongodb.event.CommandStartedEvent;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.mongo.v3_1.internal.MongoInstrumenterFactory;
import io.opentelemetry.instrumentation.mongo.v3_1.internal.TracingCommandListener;

public final class MongoInstrumentationSingletons {

  private static final Instrumenter<CommandStartedEvent, Void> INSTRUMENTER =
      MongoInstrumenterFactory.createInstrumenter(
          GlobalOpenTelemetry.get(),
          "io.opentelemetry.mongo-3.7",
          DeclarativeConfigUtil.getBoolean(
                  GlobalOpenTelemetry.get(), "java", "mongo", "statement_sanitizer", "enabled")
              .orElseGet(
                  () ->
                      DeclarativeConfigUtil.getBoolean(
                              GlobalOpenTelemetry.get(),
                              "java",
                              "db",
                              "statement_sanitizer",
                              "enabled")
                          .orElse(true)));

  public static final CommandListener LISTENER = new TracingCommandListener(INSTRUMENTER);

  public static boolean isTracingListener(CommandListener listener) {
    return listener.getClass().getName().equals(LISTENER.getClass().getName());
  }

  private MongoInstrumentationSingletons() {}
}
