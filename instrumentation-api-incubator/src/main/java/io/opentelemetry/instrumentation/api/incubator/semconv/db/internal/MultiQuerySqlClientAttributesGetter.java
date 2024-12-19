/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.db.internal;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlClientAttributesGetter;
import java.util.Collection;

/**
 * An extended version of {@link SqlClientAttributesGetter} for getting SQL database client
 * attributes for operations that run multiple distinct queries.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public interface MultiQuerySqlClientAttributesGetter<REQUEST>
    extends SqlClientAttributesGetter<REQUEST> {

  /**
   * Get the raw SQL statements. The value returned by this method is later sanitized by the {@link
   * SqlClientAttributesExtractor} before being set as span attribute.
   */
  Collection<String> getRawQueryTexts(REQUEST request);
}
