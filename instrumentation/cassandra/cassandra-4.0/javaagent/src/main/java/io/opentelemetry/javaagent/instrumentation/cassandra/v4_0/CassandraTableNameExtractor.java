/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v4_0;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/**
 * this class is separate to enable testing. when the {@link #extractTableNameFromQuery} method was
 * embedded there was an issue with visibility, even if the method was public.
 */
public class CassandraTableNameExtractor {

  private CassandraTableNameExtractor() {}

  private static final Pattern tableNameRegex =
      Pattern.compile(
          ".*(?:FROM|INTO|UPDATE|TRUNCATE|(?:CREATE|ALTER|DROP) TABLE)\\s+(?:IF (?:NOT )?EXISTS\\s+)?([A-Z1-9_]+\\.([A-Z1-9_]+)|([A-Z1-9_]+))",
          Pattern.CASE_INSENSITIVE);

  @Nullable
  public static String extractTableNameFromQuery(String query) {
    String tableName = null;
    Matcher matcher = tableNameRegex.matcher(query);
    if (matcher.find()) {
      if (matcher.group(2) != null) {
        tableName = matcher.group(2);
      } else {
        tableName = matcher.group(1);
      }
    }
    return tableName;
  }
}
