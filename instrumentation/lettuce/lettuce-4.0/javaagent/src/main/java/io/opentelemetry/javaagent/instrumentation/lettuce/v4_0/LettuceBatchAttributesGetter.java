/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v4_0;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesGetter;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues;
import javax.annotation.Nullable;

final class LettuceBatchAttributesGetter
    implements DbClientAttributesGetter<LettuceBatchRequest, Void> {

  @Override
  public String getDbSystemName(LettuceBatchRequest request) {
    return DbSystemNameIncubatingValues.REDIS;
  }

  @Override
  @Nullable
  public String getDbNamespace(LettuceBatchRequest request) {
    return null;
  }

  @Override
  @Nullable
  public String getDbQueryText(LettuceBatchRequest request) {
    return null;
  }

  @Override
  public String getDbOperationName(LettuceBatchRequest request) {
    return request.getOperationName();
  }

  @Override
  @Nullable
  public Long getDbOperationBatchSize(LettuceBatchRequest request) {
    return request.getBatchSize();
  }
}
