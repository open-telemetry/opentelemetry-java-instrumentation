/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.mongoasync.v3_3;

import com.mongodb.event.CommandListener;
import com.mongodb.event.CommandStartedEvent;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DbConfig;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.mongo.v3_1.internal.MongoInstrumenterFactory;
import io.opentelemetry.instrumentation.mongo.v3_1.internal.TracingCommandListener;

public class MongoInstrumentationSingletons {

  private static final Instrumenter<CommandStartedEvent, Void> instrumenter =
      MongoInstrumenterFactory.createInstrumenter(
          GlobalOpenTelemetry.get(),
          "io.opentelemetry.mongo-async-3.3",
          DbConfig.isQuerySanitizationEnabled(GlobalOpenTelemetry.get(), "mongo"));

  private static final CommandListener tracingListener = new TracingCommandListener(instrumenter);

  public static CommandListener tracingListener() {
    return tracingListener;
  }

  public static boolean isTracingListener(CommandListener commandListener) {
    return commandListener.getClass().getName().equals(tracingListener.getClass().getName());
  }

  private MongoInstrumentationSingletons() {}
}
