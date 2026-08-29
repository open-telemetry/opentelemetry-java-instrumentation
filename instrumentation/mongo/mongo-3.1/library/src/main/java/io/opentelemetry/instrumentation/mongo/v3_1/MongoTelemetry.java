/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.mongo.v3_1;

import static java.util.Collections.singletonList;

import com.mongodb.ServerAddress;
import com.mongodb.event.CommandListener;
import com.mongodb.event.CommandStartedEvent;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.mongo.v3_1.internal.MongoInstrumenterFactory;
import io.opentelemetry.instrumentation.mongo.v3_1.internal.MongoServerTarget;
import io.opentelemetry.instrumentation.mongo.v3_1.internal.TracingCommandListener;

// TODO this class is used for all Mongo versions. Extract to mongo-common module
/** Entrypoint to OpenTelemetry instrumentation of the MongoDB client. */
public final class MongoTelemetry {
  private final Instrumenter<CommandStartedEvent, Void> instrumenter;

  /** Returns a new {@link MongoTelemetry} configured with the given {@link OpenTelemetry}. */
  public static MongoTelemetry create(OpenTelemetry openTelemetry) {
    return builder(openTelemetry).build();
  }

  /**
   * Returns a new {@link MongoTelemetryBuilder} configured with the given {@link OpenTelemetry}.
   */
  public static MongoTelemetryBuilder builder(OpenTelemetry openTelemetry) {
    return new MongoTelemetryBuilder(openTelemetry, "io.opentelemetry.mongo-3.1");
  }

  MongoTelemetry(
      OpenTelemetry openTelemetry,
      String instrumentationName,
      boolean querySanitizationEnabled,
      int maxNormalizedQueryLength) {
    this.instrumenter =
        MongoInstrumenterFactory.createInstrumenter(
            openTelemetry, instrumentationName, querySanitizationEnabled, maxNormalizedQueryLength);
  }

  /**
   * Returns a new {@link CommandListener} that can be used with methods like {@link
   * com.mongodb.MongoClientOptions.Builder#addCommandListener(CommandListener)}.
   */
  public CommandListener createCommandListener() {
    return new TracingCommandListener(instrumenter);
  }

  /**
   * Returns a new {@link CommandListener} for a client configured with exactly the given server
   * address.
   *
   * <p>The configured address is reported as the stable logical server target. The server selected
   * for each command is reported separately as the network peer.
   */
  public CommandListener createCommandListener(ServerAddress configuredServerAddress) {
    return new TracingCommandListener(
        instrumenter, MongoServerTarget.seeds(singletonList(configuredServerAddress)));
  }
}
