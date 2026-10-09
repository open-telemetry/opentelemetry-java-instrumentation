/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.geode.v1_4;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesGetter;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbServerTarget;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues;
import javax.annotation.Nullable;

final class GeodeDbAttributesGetter implements DbClientAttributesGetter<GeodeRequest, Void> {

  @Override
  public String getDbSystemName(GeodeRequest request) {
    return DbSystemNameIncubatingValues.GEODE;
  }

  @Override
  @Nullable
  public String getDbNamespace(GeodeRequest request) {
    return null;
  }

  @Override
  @Nullable
  public String getDbCollectionName(GeodeRequest request) {
    return request.getRegion().getName();
  }

  @Override
  @Nullable
  public String getDbQueryText(GeodeRequest request) {
    return null;
  }

  @Override
  @Nullable
  public String getDbOperationName(GeodeRequest request) {
    return request.getOperationName();
  }

  @Override
  @Nullable
  public String getServerAddress(GeodeRequest request) {
    DbServerTarget target = request.getServerTarget();
    return target == null ? null : target.getAddress();
  }

  @Override
  @Nullable
  public Integer getServerPort(GeodeRequest request) {
    DbServerTarget target = request.getServerTarget();
    return target == null ? null : target.getPort();
  }
}
