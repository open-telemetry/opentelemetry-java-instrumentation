/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.mongo.v3_1;

import io.opentelemetry.api.OpenTelemetry;

/** A builder of {@link MongoTracing}. */
public final class MongoTracingBuilder {

  // Visible for testing
  static final int DEFAULT_MAX_NORMALIZED_QUERY_LENGTH = 32 * 1024;

  private final OpenTelemetry openTelemetry;

  private int maxNormalizedQueryLength = DEFAULT_MAX_NORMALIZED_QUERY_LENGTH;

  MongoTracingBuilder(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  /**
   * Sets the max length of recorded queries after normalization. Defaults to {@value
   * DEFAULT_MAX_NORMALIZED_QUERY_LENGTH}.
   */
  public MongoTracingBuilder setMaxNormalizedQueryLength(int maxNormalizedQueryLength) {
    this.maxNormalizedQueryLength = maxNormalizedQueryLength;
    return this;
  }

  /** Returns a new {@link MongoTracing} with the settings of this {@link MongoTracingBuilder}. */
  public MongoTracing build() {
    return new MongoTracing(openTelemetry, maxNormalizedQueryLength);
  }
}
