/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.db;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.instrumentation.api.db.SqlStatementInfo;
import io.opentelemetry.instrumentation.api.db.SqlStatementSanitizer;
import javax.annotation.Nullable;

/**
 * Extractor of <a
 * href="https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/trace/semantic_conventions/database.md">database
 * attributes</a>. This class is designed with SQL (or SQL-like) database clients in mind. Aside
 * from adding the same attributes as {@link DbAttributesExtractor}, it has two more features:
 *
 * <ul>
 *   <li>It sanitizes the raw SQL query and removes all parameters;
 *   <li>It enables adding the table name extracted by the sanitizer as a parameter.
 * </ul>
 */
public abstract class SqlAttributesExtractor<REQUEST, RESPONSE>
    extends DbAttributesExtractor<REQUEST, RESPONSE> {

  @Override
  public final void onStart(AttributesBuilder attributes, REQUEST request) {
    super.onStart(attributes, request);
    AttributeKey<String> dbTable = dbTableAttribute();
    if (dbTable != null) {
      set(attributes, dbTable, table(request));
    }
  }

  @Nullable
  @Override
  protected final String statement(REQUEST request) {
    return sanitize(request).getFullStatement();
  }

  @Nullable
  @Override
  protected final String operation(REQUEST request) {
    return sanitize(request).getOperation();
  }

  @Nullable
  protected final String table(REQUEST request) {
    return sanitize(request).getTable();
  }

  private SqlStatementInfo sanitize(REQUEST request) {
    // sanitized statement is cached
    return SqlStatementSanitizer.sanitize(rawStatement(request));
  }

  @Nullable
  protected abstract AttributeKey<String> dbTableAttribute();

  @Nullable
  protected abstract String rawStatement(REQUEST request);
}
