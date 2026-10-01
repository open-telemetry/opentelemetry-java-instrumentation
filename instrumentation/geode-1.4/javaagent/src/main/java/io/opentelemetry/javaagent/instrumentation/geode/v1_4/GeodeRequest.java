/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.geode.v1_4;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;

import com.google.auto.value.AutoValue;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbServerTarget;
import javax.annotation.Nullable;
import org.apache.geode.cache.Region;

@AutoValue
abstract class GeodeRequest {

  static GeodeRequest create(
      Region<?, ?> region, String operationName, @Nullable String queryText) {
    return new AutoValue_GeodeRequest(
        region,
        operationName,
        queryText,
        emitStableDatabaseSemconv() ? GeodeServerTargets.get(region) : null);
  }

  abstract Region<?, ?> getRegion();

  abstract String getOperationName();

  @Nullable
  abstract String getQueryText();

  @Nullable
  abstract DbServerTarget getServerTarget();
}
