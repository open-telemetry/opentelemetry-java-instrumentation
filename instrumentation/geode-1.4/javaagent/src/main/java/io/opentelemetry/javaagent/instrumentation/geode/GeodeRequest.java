/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.geode;

import com.google.auto.value.AutoValue;
import javax.annotation.Nullable;
import org.apache.geode.cache.Region;

@AutoValue
public abstract class GeodeRequest {

  public static GeodeRequest create(
      Region<?, ?> region, String operationName, @Nullable String queryText) {
    return new AutoValue_GeodeRequest(region, operationName, queryText);
  }

  public abstract Region<?, ?> getRegion();

  public abstract String getOperationName();

  @Nullable
  public abstract String getQueryText();
}
