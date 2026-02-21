/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.db;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import javax.annotation.Nullable;

/** A builder of {@link SqlClientAttributesExtractor}. */
public final class SqlClientAttributesExtractorBuilder<REQUEST, RESPONSE> {

  // copied from DbIncubatingAttributes
  private static final AttributeKey<String> DB_SQL_TABLE = AttributeKey.stringKey("db.sql.table");

  final SqlClientAttributesGetter<REQUEST, RESPONSE> getter;
  @Nullable AttributeKey<String> oldSemconvTableAttribute = DB_SQL_TABLE;
  boolean querySanitizationEnabled = true;
  boolean querySanitizationAnsiQuotes = false;
  boolean captureQueryParameters = false;

  SqlClientAttributesExtractorBuilder(SqlClientAttributesGetter<REQUEST, RESPONSE> getter) {
    this.getter = getter;
  }

  /**
   * Sets the attribute key for the old semconv table attribute. Pass {@code null} to disable
   * emitting any table attribute under old semconv.
   *
   * @deprecated new semantic conventions always use db.collection.name
   */
  @CanIgnoreReturnValue
  @Deprecated // to be removed in 3.0
  public SqlClientAttributesExtractorBuilder<REQUEST, RESPONSE> setTableAttribute(
      @Nullable AttributeKey<String> oldSemconvTableAttribute) {
    this.oldSemconvTableAttribute = oldSemconvTableAttribute;
    return this;
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
   * Sets whether the SQL sanitizer should treat double-quoted fragments as string literals or
   * identifiers. By default, double quotes are used for string literals. When the sanitizer is able
   * to detect that the used database does not support double-quoted string literals then this flag
   * will be automatically switched.
   */
  @CanIgnoreReturnValue
  public SqlClientAttributesExtractorBuilder<REQUEST, RESPONSE> setQuerySanitizationAnsiQuotes(
      boolean querySanitizationAnsiQuotes) {
    this.querySanitizationAnsiQuotes = querySanitizationAnsiQuotes;
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
   * Returns a new {@link SqlClientAttributesExtractor} with the settings of this {@link
   * SqlClientAttributesExtractorBuilder}.
   */
  public AttributesExtractor<REQUEST, RESPONSE> build() {
    return new SqlClientAttributesExtractor<>(
        getter,
        oldSemconvTableAttribute,
        querySanitizationEnabled,
        querySanitizationAnsiQuotes,
        captureQueryParameters);
  }
}
