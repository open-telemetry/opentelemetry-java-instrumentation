/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.db;

import io.opentelemetry.api.common.AttributeKey;

/** A builder of {@link SqlClientAttributesExtractor}. */
public final class SqlClientAttributesExtractorBuilder<REQUEST, RESPONSE> {

  final SqlClientAttributesGetter<REQUEST> getter;
  AttributeKey<String> dbTableAttribute;

  SqlClientAttributesExtractorBuilder(SqlClientAttributesGetter<REQUEST> getter) {
    this.getter = getter;
  }

  /**
   * Configures the extractor to capture the table returned by {@link
   * SqlClientAttributesGetter#table(Object)} as span attribute.
   *
   * @param dbTableAttribute The {@link AttributeKey} under which the value returned by {@link
   *     SqlClientAttributesGetter#table(Object)} will be stored.
   */
  public SqlClientAttributesExtractorBuilder<REQUEST, RESPONSE> captureTable(
      AttributeKey<String> dbTableAttribute) {
    this.dbTableAttribute = dbTableAttribute;
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
