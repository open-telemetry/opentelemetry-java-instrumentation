/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.db;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;

/** A builder of {@link DbClientAttributesExtractor}. */
public final class DbClientAttributesExtractorBuilder<REQUEST, RESPONSE> {

  final DbClientAttributesGetter<REQUEST, RESPONSE> getter;
  boolean captureQueryParameters = false;

  DbClientAttributesExtractorBuilder(DbClientAttributesGetter<REQUEST, RESPONSE> getter) {
    this.getter = getter;
  }

  /**
   * Sets whether the query parameters should be captured as span attributes named {@code
   * db.query.parameter.<key>}. Disabled by default.
   *
   * <p>WARNING: captured query parameters may contain sensitive information such as passwords,
   * personally identifiable information or protected health info.
   */
  @CanIgnoreReturnValue
  public DbClientAttributesExtractorBuilder<REQUEST, RESPONSE> setCaptureQueryParameters(
      boolean captureQueryParameters) {
    this.captureQueryParameters = captureQueryParameters;
    return this;
  }

  /**
   * Returns a new {@link DbClientAttributesExtractor} with the settings of this {@link
   * DbClientAttributesExtractorBuilder}.
   */
  public AttributesExtractor<REQUEST, RESPONSE> build() {
    return new DbClientAttributesExtractor<>(getter, captureQueryParameters);
  }
}
