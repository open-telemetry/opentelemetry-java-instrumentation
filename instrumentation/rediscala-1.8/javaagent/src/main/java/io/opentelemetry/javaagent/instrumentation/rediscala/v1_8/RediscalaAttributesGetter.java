/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rediscala.v1_8;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesGetter;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues;
import javax.annotation.Nullable;

final class RediscalaAttributesGetter implements DbClientAttributesGetter<RediscalaRequest, Void> {

  @Override
  public String getDbSystemName(RediscalaRequest request) {
    return DbSystemNameIncubatingValues.REDIS;
  }

  @Override
  @Nullable
  public String getDbNamespace(RediscalaRequest request) {
    return null;
  }

  @Override
  @Nullable
  public String getDbQueryText(RediscalaRequest request) {
    return null;
  }

  @Override
  public String getDbOperationName(RediscalaRequest request) {
    return request.getOperationName();
  }

  @Override
  @Nullable
  public Long getDbOperationBatchSize(RediscalaRequest request) {
    return request.getBatchSize();
  }
}
