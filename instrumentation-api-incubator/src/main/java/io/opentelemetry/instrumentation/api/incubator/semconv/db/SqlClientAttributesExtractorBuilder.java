/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.db;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;

/** A builder of {@link SqlClientAttributesExtractor}. */
public final class SqlClientAttributesExtractorBuilder<REQUEST, RESPONSE> {

  final SqlClientAttributesGetter<REQUEST, RESPONSE> getter;
  boolean querySanitizationEnabled = true;
  boolean captureQueryParameters = false;
  boolean singleOperationAndCollection = false;

  SqlClientAttributesExtractorBuilder(SqlClientAttributesGetter<REQUEST, RESPONSE> getter) {
    this.getter = getter;
  }

  /**
   * Sets whether the {@code db.query.text} attribute extracted by the constructed {@link
   * SqlClientAttributesExtractor} should be sanitized. If set to {@code true}, all parameters that
   * can potentially contain sensitive information will be masked. Enabled by default.
   */
  @CanIgnoreReturnValue
  public SqlClientAttributesExtractorBuilder<REQUEST, RESPONSE> setQuerySanitizationEnabled(
      boolean querySanitizationEnabled) {
    this.querySanitizationEnabled = querySanitizationEnabled;
    return this;
  }

  /**
   * Sets whether the query parameters should be captured as span attributes named {@code
   * db.query.parameter.<key>}. Enabling this option disables the query sanitization. Disabled by
   * default.
   *
   * <p>WARNING: captured query parameters may contain sensitive information such as passwords,
   * personally identifiable information or protected health info.
   */
  @CanIgnoreReturnValue
  public SqlClientAttributesExtractorBuilder<REQUEST, RESPONSE> setCaptureQueryParameters(
      boolean captureQueryParameters) {
    this.captureQueryParameters = captureQueryParameters;
    return this;
  }

  /**
   * Sets whether {@code db.operation.name} and {@code db.collection.name} can be derived from
   * {@code db.query.text}.
   *
   * <p>Enable this only when the database system does not support query text with multiple
   * operations or multiple collections in non-batch operations. For most instrumentations, enabling
   * this will produce invalid semantic conventions.
   */
  @CanIgnoreReturnValue
  public SqlClientAttributesExtractorBuilder<REQUEST, RESPONSE> setSingleOperationAndCollection(
      boolean singleOperationAndCollection) {
    this.singleOperationAndCollection = singleOperationAndCollection;
    return this;
  }

  /**
   * Returns a new {@link SqlClientAttributesExtractor} with the settings of this {@link
   * SqlClientAttributesExtractorBuilder}.
   */
  public AttributesExtractor<REQUEST, RESPONSE> build() {
    return new SqlClientAttributesExtractor<>(
        getter, querySanitizationEnabled, captureQueryParameters, singleOperationAndCollection);
  }
}
