/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.mongo.v3_1;

import io.opentelemetry.api.OpenTelemetry;

/** A builder of {@link MongoTelemetry}. */
public final class MongoTelemetryBuilder {

  // Visible for testing
  static final int DEFAULT_MAX_NORMALIZED_QUERY_LENGTH = 32 * 1024;

  private final OpenTelemetry openTelemetry;

  private int maxNormalizedQueryLength = DEFAULT_MAX_NORMALIZED_QUERY_LENGTH;

  MongoTelemetryBuilder(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  /**
   * Sets the max length of recorded queries after normalization. Defaults to {@value
   * DEFAULT_MAX_NORMALIZED_QUERY_LENGTH}.
   */
  public MongoTelemetryBuilder setMaxNormalizedQueryLength(int maxNormalizedQueryLength) {
    this.maxNormalizedQueryLength = maxNormalizedQueryLength;
    return this;
  }

  /**
   * Returns a new {@link MongoTelemetry} with the settings of this {@link MongoTelemetryBuilder}.
   */
  public MongoTelemetry build() {
    return new MongoTelemetry(openTelemetry, maxNormalizedQueryLength);
  }
}
