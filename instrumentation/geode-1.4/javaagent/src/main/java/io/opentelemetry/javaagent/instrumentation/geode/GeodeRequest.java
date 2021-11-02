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

  public static GeodeRequest create(Region<?, ?> region, String operation, @Nullable String query) {
    return new AutoValue_GeodeRequest(region, operation, query);
  }

  public abstract Region<?, ?> getRegion();

  public abstract String getOperation();

  @Nullable
  public abstract String getQuery();
}
