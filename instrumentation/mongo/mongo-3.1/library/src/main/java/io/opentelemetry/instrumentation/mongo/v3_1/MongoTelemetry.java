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
   *
   * <p>Use this method when the client's configured seed addresses are not available. If they are
   * available, use {@link #createCommandListener(List)} so that stable database semantic
   * conventions can derive {@code server.address} and {@code server.port} from the configured
   * target.
   */
  public CommandListener createCommandListener() {
    return new TracingCommandListener(instrumenter);
  }

  /**
   * Returns a new {@link CommandListener} using the supplied seed list to derive the client's
   * logical MongoDB server target.
   *
   * <p>Use this overload when you have the seed addresses from the client's configuration. Pass the
   * complete seed list used to configure the client.
   *
   * <p>The supplied addresses are used to derive the stable {@code server.address} and {@code
   * server.port} attributes when stable database semantic conventions are enabled.
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
