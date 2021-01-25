/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.geode;

import static io.opentelemetry.javaagent.instrumentation.api.db.QueryNormalizationConfig.isQueryNormalizationEnabled;

import io.opentelemetry.javaagent.instrumentation.api.db.sanitizer.SqlSanitizer;

public final class GeodeQueryNormalizer {
  private static final boolean NORMALIZATION_ENABLED =
      isQueryNormalizationEnabled("geode", "geode-1.4");

  public static String normalize(String query) {
    if (!NORMALIZATION_ENABLED || query == null) {
      return query;
    }
    return SqlSanitizer.sanitize(query).getFullStatement();
  }

  private GeodeQueryNormalizer() {}
}
