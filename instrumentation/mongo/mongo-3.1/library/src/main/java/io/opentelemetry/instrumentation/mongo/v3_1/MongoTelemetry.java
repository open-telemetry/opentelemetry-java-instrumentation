/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.mongo.v3_1;

import com.mongodb.event.CommandListener;
import com.mongodb.event.CommandStartedEvent;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;

// TODO this class is used for all Mongo versions. Extract to mongo-common module
/** Entrypoint to OpenTelemetry instrumentation of the MongoDB client. */
public final class MongoTelemetry {

  /** Returns a new {@link MongoTelemetry} configured with the given {@link OpenTelemetry}. */
  public static MongoTelemetry create(OpenTelemetry openTelemetry) {
    return builder(openTelemetry).build();
  }

  /** Returns a new {@link MongoTelemetry} configured with the given {@link OpenTelemetry}. */
  public static MongoTelemetry create(OpenTelemetry openTelemetry, String instrumentationName) {
    return builder(openTelemetry, instrumentationName).build();
  }

  /**
   * Returns a new {@link MongoTelemetryBuilder} configured with the given {@link OpenTelemetry}.
   */
  public static MongoTelemetryBuilder builder(
      OpenTelemetry openTelemetry, String instrumentationName) {
    return new MongoTelemetryBuilder(openTelemetry, instrumentationName);
  }

  /**
   * Returns a new {@link MongoTelemetryBuilder} configured with the given {@link OpenTelemetry}.
   */
  public static MongoTelemetryBuilder builder(OpenTelemetry openTelemetry) {
    return new MongoTelemetryBuilder(openTelemetry, "io.opentelemetry.mongo-3.1");
  }

  private final Instrumenter<CommandStartedEvent, Void> instrumenter;

  MongoTelemetry(
      OpenTelemetry openTelemetry,
      String instrumentationName,
      boolean statementSanitizationEnabled,
      int maxNormalizedQueryLength) {
    this.instrumenter =
        MongoInstrumenterFactory.createInstrumenter(
            openTelemetry,
            instrumentationName,
            statementSanitizationEnabled,
            maxNormalizedQueryLength);
  }

  /**
   * Returns a new {@link CommandListener} that can be used with methods like {@link
   * com.mongodb.MongoClientOptions.Builder#addCommandListener(CommandListener)}.
   */
  public CommandListener newCommandListener() {
    return new TracingCommandListener(instrumenter);
  }
}
