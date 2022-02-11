/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.db;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.api.db.SqlStatementSanitizer;
import javax.annotation.Nullable;

/**
 * An interface for getting SQL database client attributes. Aside from getting the same attributes
 * as {@link DbClientAttributesGetter}, by default it sanitizes the raw SQL query and removes all
 * parameters before passing it along to the {@link SqlClientAttributesExtractor}.
 *
 * <p>Instrumentation authors will create implementations of this interface for their specific
 * library/framework. It will be used by the {@link SqlClientAttributesExtractor} to obtain the
 * various SQL database client attributes in a type-generic way.
 *
 * <p>If an attribute is not available in this library, it is appropriate to return {@code null}
 * from the attribute methods, but implement as many as possible for best compliance with the
 * OpenTelemetry specification.
 */
public interface SqlClientAttributesGetter<REQUEST> extends DbClientAttributesGetter<REQUEST> {

  @Nullable
  @Override
  default String statement(REQUEST request) {
    // sanitized statement is cached
    return SqlStatementSanitizer.sanitize(rawStatement(request)).getFullStatement();
  }

  @Nullable
  @Override
  default String operation(REQUEST request) {
    // sanitized statement is cached
    return SqlStatementSanitizer.sanitize(rawStatement(request)).getOperation();
  }

  /**
   * Get the main table used in the SQL statement. This attribute is added only if the {@link
   * SqlClientAttributesExtractor} is configured with the proper attribute key.
   *
   * @see SqlClientAttributesExtractorBuilder#captureTable(AttributeKey)
   */
  @Nullable
  default String table(REQUEST request) {
    // sanitized statement is cached
    return SqlStatementSanitizer.sanitize(rawStatement(request)).getTable();
  }

  @Nullable
  String rawStatement(REQUEST request);
}
