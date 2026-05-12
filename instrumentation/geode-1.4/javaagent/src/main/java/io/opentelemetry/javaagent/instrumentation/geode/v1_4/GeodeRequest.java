/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.geode.v1_4;

import com.google.auto.value.AutoValue;
import javax.annotation.Nullable;
import org.apache.geode.cache.Region;

@AutoValue
abstract class GeodeRequest {

  static GeodeRequest create(
      Region<?, ?> region, String operationName, @Nullable String queryText) {
    return new AutoValue_GeodeRequest(region, operationName, queryText);
  }

  abstract Region<?, ?> getRegion();

  abstract String getOperationName();

  @Nullable
  abstract String getQueryText();
}
