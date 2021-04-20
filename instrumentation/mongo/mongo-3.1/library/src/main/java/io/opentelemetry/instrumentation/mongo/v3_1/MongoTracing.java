/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.mongo.v3_1;

import com.mongodb.event.CommandListener;
import io.opentelemetry.api.OpenTelemetry;

/** Entrypoint to OpenTelemetry instrumentation of the MongoDB client. */
public final class MongoTracing {

  /** Returns a new {@link MongoTracing} configured with the given {@link OpenTelemetry}. */
  public static MongoTracing create(OpenTelemetry openTelemetry) {
    return newBuilder(openTelemetry).build();
  }

  /** Returns a new {@link MongoTracingBuilder} configured with the given {@link OpenTelemetry}. */
  public static MongoTracingBuilder newBuilder(OpenTelemetry openTelemetry) {
    return new MongoTracingBuilder(openTelemetry);
  }

  private final MongoClientTracer tracer;

  MongoTracing(OpenTelemetry openTelemetry, int maxNormalizedQueryLength) {
    this.tracer = new MongoClientTracer(openTelemetry, maxNormalizedQueryLength);
  }

  /**
   * Returns a new {@link CommandListener} that can be used with methods like {@link
   * com.mongodb.MongoClientOptions.Builder#addCommandListener(CommandListener)}.
   */
  public CommandListener newCommandListener() {
    return new TracingCommandListener(tracer);
  }
}
