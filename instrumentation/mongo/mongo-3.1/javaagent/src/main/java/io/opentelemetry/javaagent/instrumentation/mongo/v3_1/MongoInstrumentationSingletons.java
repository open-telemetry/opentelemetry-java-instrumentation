/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.mongo.v3_1;

import com.mongodb.event.CommandListener;
import com.mongodb.event.CommandStartedEvent;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.mongo.v3_1.MongoInstrumenterFactory;
import io.opentelemetry.instrumentation.mongo.v3_1.TracingCommandListener;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.bootstrap.internal.AgentInstrumentationConfig;

public final class MongoInstrumentationSingletons {

  private static final Instrumenter<CommandStartedEvent, Void> INSTRUMENTER =
      MongoInstrumenterFactory.createInstrumenter(
          GlobalOpenTelemetry.get(),
          "io.opentelemetry.mongo-3.1",
          AgentInstrumentationConfig.get()
              .getBoolean(
                  "otel.instrumentation.mongo.statement-sanitizer.enabled",
                  AgentCommonConfig.get().isStatementSanitizationEnabled()),
          32 * 1024); // DEFAULT_MAX_NORMALIZED_QUERY_LENGTH

  public static final CommandListener LISTENER = new TracingCommandListener(INSTRUMENTER);

  public static boolean isTracingListener(CommandListener listener) {
    return listener.getClass().getName().equals(LISTENER.getClass().getName());
  }

  private MongoInstrumentationSingletons() {}
}
