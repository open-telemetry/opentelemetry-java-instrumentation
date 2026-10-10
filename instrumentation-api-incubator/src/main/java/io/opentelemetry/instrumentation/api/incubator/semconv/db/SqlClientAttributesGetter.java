/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.db;

import java.util.Collection;

/**
 * An interface for getting SQL database client attributes.
 *
 * <p>Instrumentation authors will create implementations of this interface for their specific
 * library/framework. It will be used by the {@link SqlClientAttributesExtractor} to obtain the
 * various SQL database client attributes in a type-generic way.
 *
 * <p>If an attribute is not available in this library, it is appropriate to return {@code null}
 * from the attribute methods, but implement as many as possible for best compliance with the
 * OpenTelemetry specification.
 */
public interface SqlClientAttributesGetter<REQUEST, RESPONSE>
    extends DbClientAttributesGetter<REQUEST, RESPONSE> {

  /**
   * SQL instrumentations must not override or call this method.
   *
   * <p>Provide raw query text through {@link #getRawQueryTexts(REQUEST)} instead. When the database
   * system does not support query text with multiple operations in non-batch operations, enable
   * {@link SqlClientAttributesExtractorBuilder#setSingleOperationAndCollection(boolean)} and {@link
   * SqlClientAttributesExtractor} will derive {@code db.operation.name} from {@code db.query.text}.
   *
   * @throws UnsupportedOperationException always
   */
  @Override
  default String getDbOperationName(REQUEST request) {
    throw new UnsupportedOperationException(
        "SQL instrumentations derive db.operation.name from the raw query text");
  }

  /**
   * SQL instrumentations must not override or call this method.
   *
   * <p>Provide raw query text through {@link #getRawQueryTexts(REQUEST)} instead. {@link
   * SqlClientAttributesExtractor} will derive {@code db.query.text} from the raw query text.
   *
   * @throws UnsupportedOperationException always
   */
  @Override
  default String getDbQueryText(REQUEST request) {
    throw new UnsupportedOperationException(
        "SQL instrumentations derive db.query.text from the raw query text");
  }

  /**
   * SQL instrumentations must not override or call this method.
   *
   * <p>Provide raw query text through {@link #getRawQueryTexts(REQUEST)} instead. {@link
   * SqlClientAttributesExtractor} will derive {@code db.query.summary} from the raw query text.
   *
   * @throws UnsupportedOperationException always
   */
  @Override
  default String getDbQuerySummary(REQUEST request) {
    throw new UnsupportedOperationException(
        "SQL instrumentations derive db.query.summary from the raw query text");
  }

  /**
   * SQL instrumentations must not override or call this method.
   *
   * <p>Provide raw query text through {@link #getRawQueryTexts(REQUEST)} instead. When the database
   * system does not support query text with multiple collections in non-batch operations, enable
   * {@link SqlClientAttributesExtractorBuilder#setSingleOperationAndCollection(boolean)} and {@link
   * SqlClientAttributesExtractor} will derive {@code db.collection.name} from {@code
   * db.query.text}. Do not enable that option when the database system supports query text with
   * multiple collections in non-batch operations.
   *
   * @throws UnsupportedOperationException always
   */
  @Override
  default String getDbCollectionName(REQUEST request) {
    throw new UnsupportedOperationException(
        "SQL instrumentations derive db.collection.name from the raw query text");
  }

  /** Returns the SQL dialect used by the database. */
  SqlDialect getSqlDialect(REQUEST request);

  /**
   * Get the raw SQL query texts. The values returned by this method are later sanitized by the
   * {@link SqlClientAttributesExtractor} before being set as span attribute.
   *
   * <p>If {@code request} is not a batch query, then this method should return a collection with a
   * single element.
   */
  Collection<String> getRawQueryTexts(REQUEST request);

  /**
   * Returns whether the query at {@code queryIndex} in {@link #getRawQueryTexts(Object)} is
   * parameterized. Prepared statements are always considered parameterized even if no parameters
   * are bound. By using a parameterized query the user is giving a strong signal that any sensitive
   * data will be passed as parameter values, and so the query does not need to be sanitized. See <a
   * href="https://github.com/open-telemetry/semantic-conventions/blob/main/docs/db/database-spans.md#sanitization-of-dbquerytext">sanitization
   * of db.query.text</a>.
   *
   * <p>The {@code queryIndex} is zero-based and follows the iteration order of {@link
   * #getRawQueryTexts(Object)}. This supports batch operations where individual entries may have
   * different parameterization.
   */
  // TODO: make this required to implement
  default boolean isParameterizedQuery(REQUEST request, int queryIndex) {
    return false;
  }
}
