/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.geode;

import com.google.auto.value.AutoValue;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlStatementInfo;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlStatementSanitizer;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import java.util.Locale;
import javax.annotation.Nullable;
import org.apache.geode.cache.Region;

@AutoValue
public abstract class GeodeRequest {

  private static final SqlStatementSanitizer sanitizer =
      SqlStatementSanitizer.create(AgentCommonConfig.get().isStatementSanitizationEnabled());

  public static GeodeRequest create(Region<?, ?> region, String operation, @Nullable String query) {
    SqlStatementInfo sqlStatementInfo = null;
    if (query != null) {
      // Use regular sanitize for query text
      SqlStatementInfo sanitized = sanitizer.sanitize(query);
      // Manually build query summary for stable semconv
      String querySummary = buildQuerySummary(query, region.getName());
      // Recreate with summary
      sqlStatementInfo =
          SqlStatementInfo.createWithSummary(sanitized.getQueryText(), null, querySummary);
    }
    return new AutoValue_GeodeRequest(region, operation, sqlStatementInfo);
  }

  @Nullable
  private static String buildQuerySummary(String query, String regionName) {
    // Extract operation (first word) from query
    String trimmed = query.trim();
    int firstSpace = trimmed.indexOf(' ');
    if (firstSpace > 0) {
      String operation = trimmed.substring(0, firstSpace).toUpperCase(Locale.ROOT);
      // Return operation + region name (e.g., "SELECT test-region")
      return operation + " " + regionName;
    }
    return null;
  }

  public abstract Region<?, ?> getRegion();

  public abstract String getOperation();

  @Nullable
  public abstract SqlStatementInfo getSqlStatementInfo();
}
