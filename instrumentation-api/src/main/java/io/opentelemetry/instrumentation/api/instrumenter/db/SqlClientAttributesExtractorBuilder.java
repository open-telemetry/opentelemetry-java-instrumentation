/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.db;

import static java.util.Objects.requireNonNull;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;

/** A builder of {@link SqlClientAttributesExtractor}. */
public final class SqlClientAttributesExtractorBuilder<REQUEST, RESPONSE> {

  final SqlClientAttributesGetter<REQUEST> getter;
  AttributeKey<String> dbTableAttribute = SemanticAttributes.DB_SQL_TABLE;

  SqlClientAttributesExtractorBuilder(SqlClientAttributesGetter<REQUEST> getter) {
    this.getter = getter;
  }

  /**
   * Configures the extractor to set the table value extracted by the {@link
   * SqlClientAttributesExtractor} under the {@code dbTableAttribute} key. By default, the <code>
   * {@linkplain SemanticAttributes#DB_SQL_TABLE db.sql.table}</code> attribute is used.
   *
   * @param dbTableAttribute The {@link AttributeKey} under which the table extracted by the {@link
   *     SqlClientAttributesExtractor} will be stored.
   */
  public SqlClientAttributesExtractorBuilder<REQUEST, RESPONSE> setTableAttribute(
      AttributeKey<String> dbTableAttribute) {
    this.dbTableAttribute = requireNonNull(dbTableAttribute);
    return this;
  }

  /**
   * Returns a new {@link SqlClientAttributesExtractor} with the settings of this {@link
   * SqlClientAttributesExtractorBuilder}.
   */
  public SqlClientAttributesExtractor<REQUEST, RESPONSE> build() {
    return new SqlClientAttributesExtractor<>(getter, dbTableAttribute);
  }
}
