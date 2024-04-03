/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.db;

import static java.util.Objects.requireNonNull;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes;

/** A builder of {@link SqlClientAttributesExtractor}. */
public final class SqlClientAttributesExtractorBuilder<REQUEST, RESPONSE> {

  final SqlClientAttributesGetter<REQUEST> getter;
  AttributeKey<String> dbTableAttribute = DbIncubatingAttributes.DB_SQL_TABLE;
  boolean statementSanitizationEnabled = true;

  SqlClientAttributesExtractorBuilder(SqlClientAttributesGetter<REQUEST> getter) {
    this.getter = getter;
  }

  /**
   * Configures the extractor to set the table value extracted by the {@link
   * SqlClientAttributesExtractor} under the {@code dbTableAttribute} key. By default, the <code>
   * {@link DbIncubatingAttributes#DB_SQL_TABLE}</code> attribute is used.
   *
   * @param dbTableAttribute The {@link AttributeKey} under which the table extracted by the {@link
   *     SqlClientAttributesExtractor} will be stored.
   */
  @CanIgnoreReturnValue
  public SqlClientAttributesExtractorBuilder<REQUEST, RESPONSE> setTableAttribute(
      AttributeKey<String> dbTableAttribute) {
    this.dbTableAttribute = requireNonNull(dbTableAttribute);
    return this;
  }

  /**
   * Sets whether the {@code db.statement} attribute extracted by the constructed {@link
   * SqlClientAttributesExtractor} should be sanitized. If set to {@code true}, all parameters that
   * can potentially contain sensitive information will be masked. Enabled by default.
   */
  @CanIgnoreReturnValue
  public SqlClientAttributesExtractorBuilder<REQUEST, RESPONSE> setStatementSanitizationEnabled(
      boolean statementSanitizationEnabled) {
    this.statementSanitizationEnabled = statementSanitizationEnabled;
    return this;
  }

  /**
   * Returns a new {@link SqlClientAttributesExtractor} with the settings of this {@link
   * SqlClientAttributesExtractorBuilder}.
   */
  public AttributesExtractor<REQUEST, RESPONSE> build() {
    return new SqlClientAttributesExtractor<>(
        getter, dbTableAttribute, SqlStatementSanitizer.create(statementSanitizationEnabled));
  }
}
