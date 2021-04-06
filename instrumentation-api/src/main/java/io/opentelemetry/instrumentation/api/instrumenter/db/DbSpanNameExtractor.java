/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.db;

import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import org.checkerframework.checker.nullness.qual.Nullable;

final class DbSpanNameExtractor<REQUEST> implements SpanNameExtractor<REQUEST> {
  private final DbAttributesExtractor<REQUEST> attributesExtractor;
  private final SpanNameExtractor<REQUEST> defaultSpanName;

  DbSpanNameExtractor(
      DbAttributesExtractor<REQUEST> attributesExtractor,
      SpanNameExtractor<REQUEST> defaultSpanName) {
    this.attributesExtractor = attributesExtractor;
    this.defaultSpanName = defaultSpanName;
  }

  @Override
  public String extract(REQUEST request) {
    String operation = attributesExtractor.dbOperation(request);
    String dbName = attributesExtractor.dbName(request);
    if (operation == null) {
      return dbName == null ? defaultSpanName.extract(request) : dbName;
    }

    String table = getTableName(request);
    StringBuilder name = new StringBuilder(operation);
    if (dbName != null || table != null) {
      name.append(' ');
    }
    if (dbName != null) {
      name.append(dbName);
      if (table != null) {
        name.append('.');
      }
    }
    if (table != null) {
      name.append(table);
    }
    return name.toString();
  }

  @Nullable
  private String getTableName(REQUEST request) {
    if (attributesExtractor instanceof SqlAttributesExtractor) {
      return ((SqlAttributesExtractor<REQUEST>) attributesExtractor).dbTable(request);
    }
    return null;
  }
}
