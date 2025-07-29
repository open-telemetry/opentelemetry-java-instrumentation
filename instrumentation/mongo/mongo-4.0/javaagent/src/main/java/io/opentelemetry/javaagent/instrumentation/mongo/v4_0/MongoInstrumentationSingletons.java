/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.mongo.v4_0;

import com.mongodb.event.CommandListener;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.mongo.v3_1.MongoTelemetry;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.bootstrap.internal.AgentInstrumentationConfig;

public final class MongoInstrumentationSingletons {

  public static final CommandListener LISTENER =
      MongoTelemetry.builder(GlobalOpenTelemetry.get())
          .setStatementSanitizationEnabled(
              AgentInstrumentationConfig.get()
                  .getBoolean(
                      "otel.instrumentation.mongo.statement-sanitizer.enabled",
                      AgentCommonConfig.get().isStatementSanitizationEnabled()))
          .build()
          .newCommandListener();

  public static boolean isTracingListener(CommandListener listener) {
    return listener.getClass().getName().equals(LISTENER.getClass().getName());
  }

  private MongoInstrumentationSingletons() {}
}
