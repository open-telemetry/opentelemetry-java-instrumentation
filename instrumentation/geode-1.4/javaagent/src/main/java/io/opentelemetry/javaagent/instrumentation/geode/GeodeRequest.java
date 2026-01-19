/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.geode;

import com.google.auto.value.AutoValue;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlStatementInfo;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlStatementSanitizer;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import javax.annotation.Nullable;
import org.apache.geode.cache.Region;

@AutoValue
public abstract class GeodeRequest {

  private static final SqlStatementSanitizer sanitizer =
      SqlStatementSanitizer.create(AgentCommonConfig.get().isStatementSanitizationEnabled());

  public static GeodeRequest create(Region<?, ?> region, String operation, @Nullable String query) {
    SqlStatementInfo sqlStatementInfo = query != null ? sanitizer.sanitize(query) : null;
    return new AutoValue_GeodeRequest(region, operation, sqlStatementInfo);
  }

  public abstract Region<?, ?> getRegion();

  public abstract String getOperation();

  @Nullable
  abstract SqlStatementInfo getSqlStatementInfo();
}
