/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v3_0;

import static io.opentelemetry.javaagent.instrumentation.api.db.QueryNormalizationConfig.isQueryNormalizationEnabled;

import io.opentelemetry.javaagent.instrumentation.api.db.sanitizer.SqlSanitizer;

public final class CassandraQueryNormalizer {
  private static final boolean NORMALIZATION_ENABLED =
      isQueryNormalizationEnabled("cassandra", "cassandra-3.0");

  public static String normalize(String query) {
    if (!NORMALIZATION_ENABLED) {
      return query;
    }
    return SqlSanitizer.sanitize(query).getFullStatement();
  }

  private CassandraQueryNormalizer() {}
}
