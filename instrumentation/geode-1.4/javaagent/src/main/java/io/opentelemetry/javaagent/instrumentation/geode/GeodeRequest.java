/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.geode;

import com.google.auto.value.AutoValue;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.SqlStatementInfo;
import javax.annotation.Nullable;
import org.apache.geode.cache.Region;

@AutoValue
public abstract class GeodeRequest {

  public static GeodeRequest create(
      Region<?, ?> region, String operation, @Nullable SqlStatementInfo sqlStatementInfo) {
    return new AutoValue_GeodeRequest(region, operation, sqlStatementInfo);
  }

  public abstract Region<?, ?> getRegion();

  public abstract String getOperation();

  @Nullable
  public abstract SqlStatementInfo getSqlStatementInfo();
}
