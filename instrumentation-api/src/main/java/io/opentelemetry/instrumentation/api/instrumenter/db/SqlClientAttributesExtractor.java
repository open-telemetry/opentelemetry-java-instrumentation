/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.db;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;

/**
 * Extractor of <a
 * href="https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/trace/semantic_conventions/database.md">database
 * attributes</a>. This class is designed with SQL (or SQL-like) database clients in mind.
 */
public final class SqlClientAttributesExtractor<REQUEST, RESPONSE>
    extends DbCommonAttributesExtractor<REQUEST, RESPONSE, SqlClientAttributesGetter<REQUEST>> {

  /** Creates the SQL client attributes extractor with default configuration. */
  public static <REQUEST, RESPONSE> SqlClientAttributesExtractor<REQUEST, RESPONSE> create(
      SqlClientAttributesGetter<REQUEST> getter) {
    return SqlClientAttributesExtractor.<REQUEST, RESPONSE>builder(getter).build();
  }

  /**
   * Returns a new {@link SqlClientAttributesExtractorBuilder} that can be used to configure the SQL
   * client attributes extractor.
   */
  public static <REQUEST, RESPONSE> SqlClientAttributesExtractorBuilder<REQUEST, RESPONSE> builder(
      SqlClientAttributesGetter<REQUEST> getter) {
    return new SqlClientAttributesExtractorBuilder<>(getter);
  }

  private final AttributeKey<String> dbTableAttribute;

  SqlClientAttributesExtractor(
      SqlClientAttributesGetter<REQUEST> getter, AttributeKey<String> dbTableAttribute) {
    super(getter);
    this.dbTableAttribute = dbTableAttribute;
  }

  @Override
  public void onStart(AttributesBuilder attributes, Context parentContext, REQUEST request) {
    super.onStart(attributes, parentContext, request);
    if (dbTableAttribute != null) {
      set(attributes, dbTableAttribute, getter.table(request));
    }
  }
}
