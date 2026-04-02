/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.mongo.v4_0;

import com.mongodb.event.CommandListener;
import com.mongodb.event.CommandStartedEvent;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DbConfig;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.mongo.v3_1.internal.MongoInstrumenterFactory;
import io.opentelemetry.instrumentation.mongo.v3_1.internal.TracingCommandListener;

public final class MongoInstrumentationSingletons {

  private static final Instrumenter<CommandStartedEvent, Void> INSTRUMENTER =
      MongoInstrumenterFactory.createInstrumenter(
          GlobalOpenTelemetry.get(),
          "io.opentelemetry.mongo-4.0",
          DbConfig.isQuerySanitizationEnabled(GlobalOpenTelemetry.get(), "mongo"));

  public static final CommandListener LISTENER = new TracingCommandListener(INSTRUMENTER);

  public static boolean isTracingListener(CommandListener listener) {
    return listener.getClass().getName().equals(LISTENER.getClass().getName());
  }

  private MongoInstrumentationSingletons() {}
}
