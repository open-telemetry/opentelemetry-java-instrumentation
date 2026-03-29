/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.db;

import java.util.Collection;
import javax.annotation.Nullable;

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
   * SqlClientAttributesExtractor will try to populate db.operation.name based on {@link
   * #getRawQueryTexts(REQUEST)}, but this can be used to explicitly provide the operation name.
   */
  @Override
  @Nullable
  default String getDbOperationName(REQUEST request) {
    return null;
  }

  /**
   * SqlClientAttributesExtractor will try to populate db.query.text based on {@link
   * #getRawQueryTexts(REQUEST)}, but this can be used to explicitly provide the query text.
   */
  @Override
  @Nullable
  default String getDbQueryText(REQUEST request) {
    return null;
  }

  /**
   * SqlClientAttributesExtractor will try to populate db.query.summary based on {@link
   * #getRawQueryTexts(REQUEST)}, but this can be used to explicitly provide the query summary.
   */
  @Override
  @Nullable
  default String getDbQuerySummary(REQUEST request) {
    return null;
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
   * Returns whether the query is parameterized. Prepared statements are always considered
   * parameterized even if no parameters are bound. By using a parameterized query the user is
   * giving a strong signal that any sensitive data will be passed as parameter values, and so the
   * query does not need to be sanitized. See <a
   * href="https://github.com/open-telemetry/semantic-conventions/blob/main/docs/db/database-spans.md#sanitization-of-dbquerytext">sanitization
   * of db.query.text</a>.
   */
  // TODO: make this required to implement
  default boolean isParameterizedQuery(REQUEST request) {
    return false;
  }
}
