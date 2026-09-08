/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.mongo.v3_1;

import com.mongodb.ServerAddress;
import com.mongodb.event.CommandListener;
import com.mongodb.event.CommandStartedEvent;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.mongo.v3_1.internal.MongoInstrumenterFactory;
import io.opentelemetry.instrumentation.mongo.v3_1.internal.MongoServerTarget;
import io.opentelemetry.instrumentation.mongo.v3_1.internal.TracingCommandListener;
import java.util.List;

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
   * Returns a new {@link CommandListener} using the supplied seed list to derive the client's
   * logical MongoDB server target.
   *
   * <p>Use this overload when the client is configured with one or more seed addresses. The seed
   * list must contain every address from the client's configuration. Do not pass the server
   * selected for a command, discovered cluster nodes, or only a subset of the configured seeds.
   *
   * <p>The seed list is captured when the listener is created and is used only to derive stable
   * database server attributes. It does not change the client's connections or configuration.
   *
   * <p>Where the old database conventions are emitted, {@code db.connection_string} continues to
   * describe the server selected by the driver.
   *
   * @param configuredServerAddresses all seed addresses configured for the client
   * @return a command listener
   */
  public CommandListener createCommandListener(List<ServerAddress> configuredServerAddresses) {
    return new TracingCommandListener(
        instrumenter, MongoServerTarget.seeds(configuredServerAddresses));
  }
}
